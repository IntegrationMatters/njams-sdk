/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CLIENTID_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CONTENT_HEADER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.fragments.JMSChunkAssembly;
import com.im.njams.sdk.communication.fragments.RawMessage;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.utils.ClasspathValidator;
import com.im.njams.sdk.utils.StringUtils;

/**
 * JMS implementation for a Receiver.
 *
 * @author pnientiedt, krautenberg@integrationmatters.ocm
 * @version 4.0.5
 */
public class JmsReceiver extends AbstractReceiver implements MessageListener, ExceptionListener, ClasspathValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JmsReceiver.class);

    protected static final String JMS_TIMESTAMP_PROPERTY = "JMSTimestamp";
    protected static final String NJAMS_RECEIVER_PROPERTY = "NJAMS_RECEIVER";

    private Connection connection = null;
    protected Session session = null;
    private Properties properties = null;
    protected MessageConsumer consumer = null;
    private String topicName = null;
    private ObjectMapper mapper = null;
    protected Topic topic = null;
    protected boolean useMessageselector = true;
    protected String messageSelector = null;
    protected Predicate<Message> messageFilter = m -> true;
    protected long oldestMessageTime = Long.MAX_VALUE;

    private SplitSupport splitSupport = null;
    protected final JMSChunkAssembly chunkAssembly = new JMSChunkAssembly();

    /**
     * Returns the name for this Receiver. (JMS)
     *
     * @return the name of this Receiver. (JMS)
     */
    @Override
    public String getName() {
        return JmsSender.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Receiver via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value NjamsSettings#PROPERTY_JMS_CONNECTION_FACTORY}
     * <li>{@value NjamsSettings#PROPERTY_JMS_USERNAME}
     * <li>{@value NjamsSettings#PROPERTY_JMS_PASSWORD}
     * <li>{@value NjamsSettings#PROPERTY_JMS_DESTINATION}
     * <li>...
     * </ul>
     * For more look in the github FAQ of this project.
     *
     * @param props the properties needed to init
     */
    @Override
    public void init(Properties props) {
        connectionStatus = ConnectionStatus.DISCONNECTED;
        mapper = JsonSerializerFactory.getFastMapper();
        properties = props;
        if (StringUtils.isNotBlank(props.getProperty(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION))) {
            topicName = props.getProperty(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION);
        } else {
            topicName = props.getProperty(NjamsSettings.PROPERTY_JMS_DESTINATION) + ".commands";
        }
        useMessageselector =
            !"false".equalsIgnoreCase(props.getProperty(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR));

        splitSupport = new SplitSupport(props, -1);
    }

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    protected String createMessageSelector() {
        if (!useMessageselector) {
            return null;
        }
        final Collection<Njams> njamsInstances = provideNjamsInstances();
        if (njamsInstances.isEmpty()) {
            return null;
        }
        final String selector = njamsInstances.stream().map(Njams::getClientPath).map(Path::getAllPaths)
            .flatMap(Collection::stream).map(Object::toString).sorted()
            .collect(Collectors.joining("' OR NJAMS_RECEIVER = '", "NJAMS_RECEIVER = '", "'"));
        LOG.debug("Updated message selector: {}", selector);
        return selector;
    }

    /**
     * This method creates a message filter that is used when message selectors are not supported.
     */
    protected Predicate<Message> createMessageFilter() {
        if (useMessageselector) {
            return m -> true;
        }
        final Collection<Njams> njamsInstances = provideNjamsInstances();
        if (njamsInstances.isEmpty()) {
            return m -> false;
        }

        final Collection<String> paths = njamsInstances.stream()
            .map(Njams::getClientPath)
            .map(Path::getAllPaths)
            .flatMap(Collection::stream)
            .map(Object::toString)
            .collect(Collectors.toSet());
        final Predicate<Message> filter = m -> {
            try {
                // for discarding old commands in case that only durable subscription is supported
                if (m.getJMSTimestamp() > 0 && m.getJMSTimestamp() < oldestMessageTime) {
                    LOG.trace("Message discarded by time filter (getJMSTimestamp={})", m.getJMSTimestamp());
                    return false;
                }

                // the actual message selector
                if (m.propertyExists(NJAMS_RECEIVER_PROPERTY)
                    && paths.contains(m.getStringProperty(NJAMS_RECEIVER_PROPERTY))) {
                    return true;
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Message discarded by receiver filter ({})", StringUtils.headersToString(m));
                }
                return false;
            } catch (Exception e) {
                LOG.error("Failed to evaluate filter for message {}. Message discarded.",
                    StringUtils.headersToString(m), e);
                return false;
            }
        };
        LOG.debug("Updated message filter with timestamp {} and paths: {}", oldestMessageTime, paths);
        return filter;
    }

    /**
     * Has to provide all {@link Njams} instances that use this receiver for selecting the commands addressed to any
     * of these instances.
     * @return All instances that use this receiver.
     */
    protected Collection<Njams> provideNjamsInstances() {
        // the non-shared receiver only serves one Njams instance
        return Collections.singleton(njams);
    }

    /**
     * This method is called when the receiver has to connect. It can't be
     * started if init(..) hasn't been called beforehand.
     */
    @Override
    public synchronized void connect() {
        if (!isConnected()) {
            connectionStatus = ConnectionStatus.CONNECTING;

            tryToConnect(properties);

            connectionStatus = ConnectionStatus.CONNECTED;
        }
    }

    /**
     * This method tries to create a receiver with a InitialContext, connection,
     * session etc. It throws an NjamsSdkRuntimeException if any of the
     * resources throws any exception.
     *
     * @param props the Properties that are used for connecting.
     */
    private void tryToConnect(Properties props) {
        try (final JmsFactory jmsFactory = JmsFactory.find(props)) {
            LOG.trace("The InitialContext was created successfully.");

            ConnectionFactory factory = jmsFactory.createConnectionFactory();
            LOG.trace("The ConnectionFactory was created successfully.");

            connection = createConnection(props, factory);
            LOG.trace("The Connection was created successfully.");

            session = createSession(connection);
            LOG.trace("The Session was created successfully.");

            topic = jmsFactory.createTopic(session, topicName);
            LOG.trace("The Topic was created successfully.");

            consumer = createConsumer(session, topic);
            LOG.trace("The MessageConsumer was created successfully.");

            oldestMessageTime = System.currentTimeMillis() - 1000;
            startConnection(connection);
            LOG.trace("The Connection was started successfully.");

        } catch (Exception e) {
            printExceptionsOnClose(closeAll());
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    /**
     * This method creates a connection out of the properties and the factory.
     * It established a secure connection with JmsConstants.USERNAME and
     * NjamsSettings.PROPERTY_JMS_PASSWORD, or if they are not provided, creates a connection
     * that uses the default username and password.
     *
     * @param props   the properties where username and password are safed
     * @param factory the factory where the connection will be created from.
     * @return the Connection if it can be created.
     * @throws JMSException is thrown if something is wrong with the username or
     *                      password.
     */
    private Connection createConnection(Properties props, ConnectionFactory factory) throws JMSException {
        Connection con;
        if (props.containsKey(NjamsSettings.PROPERTY_JMS_USERNAME)
            && props.containsKey(NjamsSettings.PROPERTY_JMS_PASSWORD)) {
            con = factory.createConnection(props.getProperty(NjamsSettings.PROPERTY_JMS_USERNAME),
                props.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD));
        } else {
            con = factory.createConnection();
        }
        return con;
    }

    /**
     * This method creates a session to the given connection. The transacted
     * boolean has been set to false and the acknowledgeMode is
     * AUTO_ACKNOWLEDGE for the created session.
     *
     * @param con the connection that creates the session
     * @return the session if it can be created
     * @throws JMSException is thrown if something failed.
     */
    private Session createSession(Connection con) throws JMSException {
        return con.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * This method creates a MessageConsumer out of the provided session for the
     * Topic topic, and listens only to the messages of
     * {@link #messageSelector messageSelector}.
     *
     * @param sess  the session that creates the MessageConsumer to the given
     *              topic, that listens to the messages that match the messageSelector.
     * @param topic the topic to listen to
     * @return the MessageConsumer if it can be created. It listens on the given
     * topic for messages that match the
     * {@link #messageSelector messageSelector}. If a message is found,
     * this.onMessage(Message msg) is invoked.
     * @throws JMSException is thrown if the MessageConsumer can' be created.
     */
    protected MessageConsumer createConsumer(Session sess, Topic topic) throws JMSException {
        final MessageConsumer cons;
        LOG.debug("Creating consumer with  message selector: {}", messageSelector);
        if (messageSelector == null) {
            cons = sess.createConsumer(topic);
        } else {
            cons = sess.createConsumer(topic, messageSelector);
        }
        cons.setMessageListener(this);
        return cons;
    }

    @Override
    public void setNjams(Njams njams) {
        super.setNjams(njams);
        if (njams != null) {
            updateFilters();
        }
    }

    protected void updateFilters() {
        LOG.debug("Updating selector/filter");
        messageSelector = createMessageSelector();
        messageFilter = createMessageFilter();
    }

    /**
     * This method starts the provided connection and sets this object as
     * exceptionListener.
     *
     * @param con the connection to start
     * @throws JMSException is thrown if either setting the exceptionListener
     *                      didn't work or the connection didn't start.
     */
    private void startConnection(Connection con) throws JMSException {
        con.setExceptionListener(this);
        con.start();
    }

    /**
     * This method sets the connectionStatus to DISCONNECTED and closes all
     * resources that have been safed as fields.
     *
     * @return a list of exceptions that may have been thrown by any of the
     * resources.
     */
    private List<Exception> closeAll() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
        List<Exception> exceptions = new ArrayList<>();
        if (consumer != null) {
            try {
                consumer.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close consumer correctly", ex));
            } finally {
                consumer = null;
            }

        }
        if (session != null) {
            try {
                session.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close session correctly", ex));
            } finally {
                session = null;
            }
        }
        if (connection != null) {
            try {
                connection.close();

            } catch (JMSException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close connection correctly", ex));
            } finally {
                connection = null;
            }
        }
        return exceptions;
    }

    /**
     * This method is used on close and logs all exceptions that have been given in the provided list
     * of exceptions that occurred when trying to close the JMS connection.
     *
     * @param exceptions the exceptions that will be logged.
     */
    private void printExceptionsOnClose(List<Exception> exceptions) {
        exceptions.forEach(e -> {
            String msg;
            if (e instanceof NjamsSdkRuntimeException) {
                msg = e.getMessage();
                final Throwable cause = e.getCause();
                if (cause != null) {
                    msg += ". Caused by: " + cause.toString();
                }
            } else {
                msg = e.toString();
            }
            LOG.warn(msg);
            LOG.debug(msg, e);
        });
    }

    /**
     * This method stops the Jms Receiver by closing all its resources, if its
     * status is CONNECTED.
     */
    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        List<Exception> exceptions = this.closeAll();
        if (!exceptions.isEmpty()) {
            printExceptionsOnClose(exceptions);
            LOG.warn("Unable to close {} correctly.", getClass().getSimpleName());
        } else {
            LOG.info("{} has been stopped successfully.", getClass().getSimpleName());
        }
    }

    /**
     * This method is the MessageListener implementation. It receives JMS
     * Messages automatically.
     *
     * @param msg the newly arrived JMS message.
     */
    @Override
    public void onMessage(Message msg) {
        if (!messageFilter.test(msg)) {
            return;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {}", StringUtils.messageToString(msg));
        }
        try {
            final String njamsContent = msg.getStringProperty(NJAMS_CONTENT_HEADER);
            if (!njamsContent.equalsIgnoreCase("json")) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            final String clientId = msg.getStringProperty(NJAMS_CLIENTID_HEADER);
            if (clientId != null && !clientId.equals(njams.getCommunicationSessionId())) {
                LOG.debug("Message is not for me! ClientId in Message is: {} but this nJAMS Client has Id: {}",
                    clientId, njams.getCommunicationSessionId());
                return;
            }

            final Instruction instruction = getInstruction(msg);
            if (instruction == null || suppressGetRequestHandlerInstruction(instruction, njams)) {
                return;
            }
            onInstruction(instruction);
            reply(msg.getJMSReplyTo(), msg.getJMSCorrelationID(), instruction, njams.getCommunicationSessionId());
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    /**
     * This method tries to extract an {@link Instruction} from the provided JMS message.
     * It maps the JSON string to an {@link Instruction} object.
     *
     * @param message the JSON Message
     * @return the {@link Instruction} object that was extracted or <code>null</code>, if the instruction was not yet
     * complete or it was not valid.
     */
    protected Instruction getInstruction(Message message) {
        if (!(message instanceof TextMessage)) {
            LOG.debug("Unexpected message type: {}", message.getClass());
            return null;
        }
        final RawMessage resolved = chunkAssembly.resolve((TextMessage) message);
        if (resolved == null) {
            LOG.debug("Received incomplete command message.");
            return null;
        }
        try {
            final Instruction instruction = mapper.readValue(resolved.getBody(), Instruction.class);
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing instruction: {}", resolved.getBody(), e);
        }
        LOG.warn("Illegal instruction format in: {}", resolved.getBody());
        return null;
    }

    /**
     * This method tries to reply the instructions response back to the sender.
     * Send a message to the sender that is mentioned in the message. If a
     * JmsCorrelationId is set in the message, it will be forwarded as well.
     *
     * @param replyDestination The JMS destination where the reply has to be sent to
     * @param jmsCorrelationID Optional JMS correlation ID for correlating the reply to its request.
     * @param instruction the instruction that holds the response.
     * @param clientId
     */
    protected void reply(Destination replyDestination, String jmsCorrelationID, Instruction instruction,
        String clientId) {
        MessageProducer replyProducer = null;
        try {
            replyProducer = session.createProducer(replyDestination);
            String response = mapper.writeValueAsString(instruction);

            final List<String> data = splitSupport.splitData(response);
            final String messageId = UUID.randomUUID().toString();
            LOG.debug("Sending reply {} with {} message fragments to {}: {}", messageId, data.size(), replyDestination,
                response);
            for (int i = 0; i < data.size(); i++) {
                final TextMessage responseMessage = session.createTextMessage(data.get(i));
                responseMessage.setStringProperty(NJAMS_CLIENTID_HEADER, clientId);
                if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                    responseMessage.setJMSCorrelationID(jmsCorrelationID);
                }
                splitSupport.addChunkHeaders((k, v) -> JMSChunkAssembly.setHeader(responseMessage, k, v), i,
                    data.size(), messageId);
                replyProducer.send(responseMessage);
                LOG.trace("Sent fragment {}/{}", i + 1, data.size());
            }

        } catch (InvalidDestinationException e) {
            // this request has already been answered by another instance -> ignore
            LOG.debug("Response channel already closed", e);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", topicName, e);
        } finally {
            if (replyProducer != null) {
                try {
                    replyProducer.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the {} receiver's reply producer.", getName());
                }
            }
        }
    }

    /**
     * This method logs all JMS Exceptions and tries to reconnect the connection.
     *
     * @param exception The jmsException to be logged.
     */
    @Override
    public void onException(JMSException exception) {
        LOG.trace("JMS failure", exception);
        super.onException(exception);
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    @Override
    public String[] librariesToCheck() {
        return new String[] {
            "javax.jms.Connection",
            "javax.jms.ConnectionFactory",
            "javax.jms.ExceptionListener",
            "javax.jms.JMSException",
            "javax.jms.Message",
            "javax.jms.MessageConsumer",
            "javax.jms.MessageListener",
            "javax.jms.Session",
            "javax.jms.TextMessage",
            "javax.jms.MessageProducer",
            "javax.jms.Topic",
            "javax.naming.InitialContext",
            "javax.naming.NameNotFoundException",
            "javax.naming.NamingException" };
    }

}
