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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.NjamsConnectionFactory;
import com.im.njams.sdk.settings.PropertyUtil;
import com.im.njams.sdk.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * JMS implementation for a Receiver.
 *
 * @author pnientiedt, krautenberg@integrationmatters.ocm
 * @version 4.0.5
 */
public class JmsReceiver extends AbstractReceiver implements MessageListener, ExceptionListener {

    private static final Logger LOG = LoggerFactory.getLogger(JmsReceiver.class);

    private Connection connection;
    protected Session session;
    private Properties properties;
    protected MessageConsumer consumer;
    private String topicName;
    private ObjectMapper mapper;
    protected Topic topic;
    protected String messageSelector;

    /**
     * Returns the name for this Receiver. (JMS)
     *
     * @return the name of this Receiver. (JMS)
     */
    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
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
        mapper = JsonSerializerFactory.getDefaultMapper();
        properties = props;
        if (props.containsKey(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION)) {
            topicName = props.getProperty(NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION);
        } else {
            topicName = props.getProperty(NjamsSettings.PROPERTY_JMS_DESTINATION) + ".commands";
        }

    }

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    protected String createMessageSelector() {

        Path fullPath = new Path(njams.getClientPath().toString());
        Path path = null;
        StringBuilder selector = new StringBuilder();
        for (String part : fullPath.getParts()) {
            if (path == null) {
                path = new Path(part);
            } else {
                path = path.add(part);
                selector.append(" OR ");
            }
            selector.append("NJAMS_RECEIVER = '").append(path.toString()).append('\'');
        }
        return selector.toString();
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
        InitialContext context = null;
        try {
            context = getInitialContext(props);
            LOG.trace("The InitialContext was created successfully.");

            ConnectionFactory factory = getConnectionFactory(props, context);
            LOG.trace("The ConnectionFactory was created successfully.");

            connection = createConnection(props, factory);
            LOG.trace("The Connection was created successfully.");

            session = createSession(connection);
            LOG.trace("The Session was created successfully.");

            topic = getOrCreateTopic(context, session);
            LOG.trace("The Topic was created successfully.");

            consumer = createConsumer(session, topic);
            LOG.trace("The MessageConsumer was created successfully.");

            startConnection(connection);
            LOG.trace("The Connection was started successfully.");

        } catch (Exception e) {
            printExceptions(this.closeAll(context));
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    /**
     * This method creates a new InitialContext out of the properties that are
     * given as parameter.
     *
     * @param prop the Properties that will be used to create a new
     *             InitialContext
     * @return the InitialContext that has been created.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private InitialContext getInitialContext(Properties prop) throws NamingException {
        return new InitialContext(PropertyUtil.filterAndCut(prop, NjamsSettings.PROPERTY_JMS_PREFIX));
    }

    /**
     * This method gets a ConnectionFactory out of the properties value for
     * JmsConstants.CONNECTION_FACTORY and the context that looks up for the
     * factory.
     *
     * @param props   the Properties where JmsConstants.CONNECTION_FACTORY as key
     *                should be provided.
     * @param context the context that is used to look up the connectionFactory.
     * @return the ConnectionFactory, if found.
     * @throws NamingException is thrown if something with the name is wrong.
     */
    private ConnectionFactory getConnectionFactory(Properties props, InitialContext context) throws Exception {
        return NjamsConnectionFactory.getFactory(context, props);
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
        if (props.containsKey(NjamsSettings.PROPERTY_JMS_USERNAME) && props.containsKey(NjamsSettings.PROPERTY_JMS_PASSWORD)) {
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
     * This method creates or gets a existing topic that has been specified in
     * the properties beforehand.
     *
     * @param context the context to look up for the topic, if it exists
     *                already.
     * @param session the session to create a new topic, if no topic can be
     *                found.
     * @return the topic that was created or has been found before.
     * @throws NamingException is thrown if something with the name is wrong.
     * @throws JMSException    is thrown if the topic can't be created.
     */
    private Topic getOrCreateTopic(InitialContext context, Session session) throws NamingException, JMSException {
        Topic topic;
        try {
            topic = (Topic) context.lookup(topicName);
            LOG.info("Topic {} has been found.", topicName);
        } catch (NameNotFoundException e) {
            LOG.info("Topic {} hasn't been found. Create Topic...", topicName);
            topic = session.createTopic(topicName);
        }
        return topic;
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
    @SuppressWarnings("squid:S2095")
    protected MessageConsumer createConsumer(Session sess, Topic topic) throws JMSException {
        MessageConsumer cons = sess.createConsumer(topic, messageSelector);
        cons.setMessageListener(this);
        return cons;
    }

    @Override
    public void setNjams(Njams njams) {
        super.setNjams(njams);
        messageSelector = createMessageSelector();
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
     * This method sets the connectionStatus to DISCONNECTED and closes all
     * resources that have been saved as fields. Furthermore it closes the
     * initial context hat has been provided as parameter.
     *
     * @param context the context that is tried to be closed.
     * @return a list of exceptions that may have been thrown by any of the
     * resources.
     */
    private List<Exception> closeAll(InitialContext context) {
        List<Exception> exceptions = closeAll();
        if (context != null) {
            try {
                context.close();
            } catch (NamingException ex) {
                exceptions.add(new NjamsSdkRuntimeException("Unable to close initial context correctly", ex));
            }
        }
        return exceptions;
    }

    /**
     * This method logs all exceptions that have been given in the provided list
     * of exceptions.
     *
     * @param exceptions the exceptions that wil be logged.
     */
    private void printExceptions(List<Exception> exceptions) {
        exceptions.forEach(exception -> LOG.error(exception.getMessage()));
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
            printExceptions(exceptions);
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
        try {
            final String njamsContent = msg.getStringProperty("NJAMS_CONTENT");
            if (!njamsContent.equalsIgnoreCase("json")) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            final Instruction instruction = getInstruction(msg);
            if (instruction == null) {
                return;
            }

            onInstruction(instruction);
            
            if(!CommonUtils.ignoreReplayResponseOnInstruction(instruction)) {
                reply(msg, instruction);
            }            
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    /**
     * This method tries to extract the Instruction out of the provided message.
     * It maps the Json string to an Instruction object.
     *
     * @param message the Json Message
     * @return the Instruction object that was extracted or null, if no valid
     * instruction was found or it could be parsed to an instruction object.
     */
    protected Instruction getInstruction(Message message) {
        try {
            String instructionString = ((TextMessage) message).getText();
            Instruction instruction = mapper.readValue(instructionString, Instruction.class
            );
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("MSG is not a valid Instruction");
        return null;
    }

    /**
     * This method tries to reply the instructions response back to the sender.
     * Send a message to the sender that is metioned in the message. If a
     * JmsCorrelationId is set in the message, it will be forwarded aswell.
     *
     * @param message     the destination where the response will be sent to and the
     *                    jmsCorrelationId are safed in here.
     * @param instruction the instruction that holds the response.
     */
    protected void reply(Message message, Instruction instruction) {
        MessageProducer replyProducer = null;
        try {
            replyProducer = session.createProducer(message.getJMSReplyTo());
            String response = mapper.writeValueAsString(instruction);
            final TextMessage responseMessage = session.createTextMessage();
            responseMessage.setText(response);
            final String jmsCorrelationID = message.getJMSCorrelationID();
            if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                responseMessage.setJMSCorrelationID(jmsCorrelationID);
            }
            replyProducer.send(responseMessage);
            LOG.debug("Response: {}", response);
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
     * This method logs all JMS Exceptions and tries to reconnect the
     * connection.
     *
     * @param exception The jmsException to be logged.
     */
    @Override
    public void onException(JMSException exception) {
        super.onException(new NjamsSdkRuntimeException("Transport error", exception));
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    @Override
    public String[] librariesToCheck() {
        return new String[]{
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
            "javax.naming.NamingException"};
    }

}
