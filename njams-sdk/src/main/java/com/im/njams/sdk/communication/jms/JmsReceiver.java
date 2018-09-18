/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.settings.PropertyUtil;

/**
 * JMS implementation for a Receiver.
 *
 * @author pnientiedt
 */
public class JmsReceiver extends AbstractReceiver implements MessageListener, ExceptionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsReceiver.class);

    private static final String NJAMS_CONTENT_HEADER = "NJAMS_CONTENT";

    private Connection connection;
    private ConnectionStatus connectionStatus;
    private Session session;
    private Properties properties;
    private MessageConsumer consumer;
    private MessageProducer producer;
    private String destinationName;
    private ObjectMapper mapper;
    private String clientPath;
    private String messageSelector;

    /**
     * Returns the name for this Receiver.
     *
     * @return the name of this Receiver
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
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * </ul>
     *
     * @param properties the properties neeeded to init
     */
    @Override
    public void init(Properties properties) {
        this.connectionStatus = ConnectionStatus.DISCONNECTED;
        mapper = JsonSerializerFactory.getDefaultMapper();
        this.properties = properties;
        if (properties.containsKey(JmsConstants.COMMANDS_DESTINATION)) {
            destinationName = properties.getProperty(JmsConstants.COMMANDS_DESTINATION);
        } else {
            destinationName = properties.getProperty(JmsConstants.DESTINATION) + ".commands";
        }
        createMessageSelector(properties);
    }

    /**
     * Start the new Receiver.
     */
    @Override
    public void start() {
        try {
            connect();
            LOG.info("Initialized receiver {}", JmsConstants.COMMUNICATION_NAME);
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize receiver {}\n. Pushing reconnect task to background.",
                    JmsConstants.COMMUNICATION_NAME, e);
            // trigger reconnect
            onException(null);
        }
    }

    private void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            InitialContext context = new InitialContext(PropertyUtil.filterAndCut(properties, getPropertyPrefix()));
            ConnectionFactory factory =
                    (ConnectionFactory) context.lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
            if (properties.containsKey(JmsConstants.USERNAME) && properties.containsKey(JmsConstants.PASSWORD)) {
                connection = factory.createConnection(properties.getProperty(JmsConstants.USERNAME),
                        properties.getProperty(JmsConstants.PASSWORD));
            } else {
                connection = factory.createConnection();
            }
            session = connection.createSession(false, JMSContext.CLIENT_ACKNOWLEDGE);
            Destination destination = null;
            try {
                destination = (Destination) context.lookup(this.destinationName);
            } catch (NameNotFoundException e) {
                destination = session.createQueue(this.destinationName);
            }
            consumer = session.createConsumer(destination, messageSelector);
            consumer.setMessageListener(this);
            producer = session.createProducer(destination);
            connection.setExceptionListener(this);
            connection.start();
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    /**
     * same as connect(), but no verbose logging.
     */
    private synchronized void reconnect() {
        if (isConnecting() || isConnected()) {
            return;
        }
        while (!isConnected()) {
            try {
                connect();
                LOG.info("Reconnected receiver {}", JmsConstants.COMMUNICATION_NAME);
            } catch (NjamsSdkRuntimeException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    return;
                }
            }
        }
    }

    /**
     * Stop the new Receiver.
     */
    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        if (consumer != null) {
            try {
                consumer.close();
                consumer = null;
            } catch (JMSException ex) {
                throw new NjamsSdkRuntimeException("Unable to close consumer", ex);
            }
        }
        if (producer != null) {
            try {
                producer.close();
                producer = null;
            } catch (JMSException ex) {
                throw new NjamsSdkRuntimeException("Unable to close producer", ex);
            }
        }
        if (session != null) {
            try {
                session.close();
                session = null;
            } catch (JMSException ex) {
                throw new NjamsSdkRuntimeException("Unable to close session", ex);
            }
        }
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (JMSException ex) {
                throw new NjamsSdkRuntimeException("Unable to close connection", ex);
            }
        }
    }

    /**
     * Message Listener implementation. Receives JMS Messages automatically.
     *
     * @param msg
     */
    @Override
    public void onMessage(Message msg) {
        try {
            String njamsContent = msg.getStringProperty(NJAMS_CONTENT_HEADER);
            if (!njamsContent.equalsIgnoreCase("json")) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            Instruction instruction = getInstruction(msg);
            if (instruction != null) {
                onInstruction(instruction);
                reply(msg, instruction);
            }
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    private Instruction getInstruction(Message message) {
        try {
            String instructionString = ((TextMessage) message).getText();
            Instruction instruction = mapper.readValue(instructionString, Instruction.class);
            if (instruction.getRequest() != null) {
                return instruction;
            }
        } catch (Exception e) {
            LOG.error("Error deserializing Instruction", e);
        }
        LOG.warn("MSG is not a valid Instruction");
        return null;
    }

    private void reply(Message message, Instruction instruction) {
        try (MessageProducer replyProducer = session.createProducer(message.getJMSReplyTo())) {
            String response = mapper.writeValueAsString(instruction);
            final TextMessage responseMessage = session.createTextMessage();
            responseMessage.setText(response);
            final String jmsCorrelationID = message.getJMSCorrelationID();
            if (jmsCorrelationID != null && !jmsCorrelationID.isEmpty()) {
                responseMessage.setJMSCorrelationID(jmsCorrelationID);
            }
            replyProducer.send(responseMessage);
            replyProducer.close();
            LOG.debug("Response: {}", response);
        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", destinationName, e);
        }
    }

    private void createMessageSelector(Properties properties) {
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
            selector.append("NJAMS_RECEIVER = '").append(path.toString()).append("'");
        }

        messageSelector = selector.toString();
    }

    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.CONNECTED;
    }

    public boolean isDisconnected() {
        return connectionStatus == ConnectionStatus.DISCONNECTED;
    }

    public boolean isConnecting() {
        return connectionStatus == ConnectionStatus.CONNECTING;
    }

    /**
     * Log all JMS Exceptions
     *
     * @param jmse
     */
    @Override
    public void onException(JMSException jmse) {
        if (jmse != null) {
            LOG.debug("Error in JmsReceiver. Trying to reconnect.", jmse);
        }

        stop();
        // reconnect
        Thread reconnector = new Thread() {

            @Override
            public void run() {
                reconnect();
            }
        };
        reconnector.setDaemon(true);
        reconnector.setName("Reconnect JMS receiver");
        reconnector.start();
    }

    /**
     * Return property prefix for the JmsReceiver.
     *
     * @return property prefix
     */
    @Override
    public String getPropertyPrefix() {
        return JmsConstants.PROPERTY_PREFIX;
    }

}
