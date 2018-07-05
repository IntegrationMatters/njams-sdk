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

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.PropertyUtil;
import java.util.Properties;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import org.slf4j.LoggerFactory;

/**
 * JMS implementation for a Sender.
 *
 * @author pnientiedt
 */
public class JmsSender implements Sender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private final ObjectMapper mapper;

    /**
     * Create a new JmsSender
     */
    public JmsSender() {
        this.mapper = JsonSerializerFactory.getDefaultMapper();
    }

    /**
     * Returns the value
     * {@value com.im.njams.sdk.communication.jms.JmsConstants#COMMUNICATION_NAME}
     * as name for this Sender.
     *
     * @return the name of this JmsSender
     */
    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
    }

    /**
     * Initializues this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        try {
            InitialContext context = new InitialContext(PropertyUtil.filterAndCut(properties, getPropertyPrefix()));
            ConnectionFactory factory
                    = (ConnectionFactory) context.lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
            if (properties.containsKey(JmsConstants.USERNAME) && properties.containsKey(JmsConstants.PASSWORD)) {
                connection = factory.createConnection(properties.getProperty(JmsConstants.USERNAME),
                        properties.getProperty(JmsConstants.PASSWORD));
            } else {
                connection = factory.createConnection();
            }
            session = connection.createSession(false, JMSContext.CLIENT_ACKNOWLEDGE);
            Destination destination
                    = (Destination) context.lookup(properties.getProperty(JmsConstants.DESTINATION) + ".event");
            producer = session.createProducer(destination);
            LOG.info("Initialized Sender {}", JmsConstants.COMMUNICATION_NAME);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to initialize", e);
        }
    }

    /**
     * Send the given LogMessage to the specified JMS.
     *
     * @param msg the Logmessage to send
     */
    @Override
    public void send(LogMessage msg) {
        try {
            String data = mapper.writeValueAsString(msg);
            TextMessage textMessage = session.createTextMessage(data);
            textMessage.setStringProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
            textMessage.setStringProperty(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
            textMessage.setStringProperty(Sender.NJAMS_PATH, msg.getPath());
            textMessage.setStringProperty(Sender.NJAMS_LOGID, msg.getLogId());
            producer.send(textMessage);
            LOG.debug("Send LogMessage {} to {}:\n{}", msg.getPath(), producer.getDestination(), data);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send LogMessage", e);
        }
    }

    /**
     * Send the given ProjectMessage to the specified JMS.
     *
     * @param msg the Projectmessage to send
     */
    @Override
    public void send(ProjectMessage msg) {
        try {
            String data = mapper.writeValueAsString(msg);
            TextMessage textMessage = session.createTextMessage(data);
            textMessage.setStringProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
            textMessage.setStringProperty(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
            textMessage.setStringProperty(Sender.NJAMS_PATH, msg.getPath());
            producer.send(textMessage);
            LOG.debug("Send ProjectMessage {} to {}:\n{}", msg.getPath(), producer.getDestination(), data);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send ProjectMessage", e);
        }
    }

    /**
     * Close this Sender.
     */
    @Override
    public void close() {
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
     * Return property prefix for the JmsReceiver.
     *
     * @return property prefix
     */
    @Override
    public String getPropertyPrefix() {
        return JmsConstants.PROPERTY_PREFIX;
    }

}
