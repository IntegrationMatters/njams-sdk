/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.PropertyUtil;
import com.im.njams.sdk.utils.JsonUtils;

/**
 * JMS implementation for a Sender.
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public class JmsSender extends AbstractSender implements ExceptionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    private Connection connection;
    protected Session session;
    protected MessageProducer producer;
    private Thread reconnector;

    /**
     * Initializes this Sender via the given Properties.
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
        super.init(properties);
        try {
            connect();
            LOG.debug("Initialized sender {}", JmsConstants.COMMUNICATION_NAME);
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize sender {}\n", JmsConstants.COMMUNICATION_NAME, e);
        }
    }

    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        InitialContext context = null;
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            context = new InitialContext(PropertyUtil.filterAndCut(properties, JmsConstants.PROPERTY_PREFIX + "."));
            ConnectionFactory factory = (ConnectionFactory) context
                    .lookup(properties.getProperty(JmsConstants.CONNECTION_FACTORY));
            if (properties.containsKey(JmsConstants.USERNAME) && properties.containsKey(JmsConstants.PASSWORD)) {
                connection = factory.createConnection(properties.getProperty(JmsConstants.USERNAME),
                        properties.getProperty(JmsConstants.PASSWORD));
            } else {
                connection = factory.createConnection();
            }
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Destination destination = null;
            String destinationName = properties.getProperty(JmsConstants.DESTINATION) + ".event";
            try {
                destination = (Destination) context.lookup(destinationName);
            } catch (NameNotFoundException e) {
                destination = session.createQueue(destinationName);
            }
            producer = session.createProducer(destination);
            connection.setExceptionListener(this);
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex) {
                    LOG.debug(ex.getMessage());
                } finally {
                    session = null;
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException ex) {
                    LOG.debug(ex.getMessage());
                } finally {
                    connection = null;
                }
            }
            throw new NjamsSdkRuntimeException("Unable to connect", e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                    context = null;
                } catch (NamingException e) {
                }
            }
        }

    }

    /**
     * Send the given LogMessage to the specified JMS.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(LogMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_EVENT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send LogMessage {} to {}:\n{}", msg.getPath(), producer.getDestination(), data);
            } else {
                LOG.debug("Send Logmessage for {} to {}", msg.getPath(), producer.getDestination());
            }

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
    protected void send(ProjectMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_PROJECT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send ProjectMessage {} to {}:\n{}", msg.getPath(), producer.getDestination(), data);
            } else {
                LOG.debug("Send ProjectMessage for {} to {}", msg.getPath(), producer.getDestination());
            }

        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send ProjectMessage", e);
        }
    }

    /**
     * Send the given TraceMessage to the specifies JMS
     *
     * @param msg the Tracemessage to send
     */
    @Override
    protected void send(TraceMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_TRACE, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send TraceMessage {} to {}:\n{}", msg.getPath(), producer.getDestination(), data);
            } else {
                LOG.debug("Send TraceMessage for {} to {}", msg.getPath(), producer.getDestination());
            }
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send TraceMessage", e);
        }
    }

    protected void sendMessage(CommonMessage msg, String messageType, String data)
            throws JMSException, InterruptedException {
        TextMessage textMessage = session.createTextMessage(data);
        if (msg instanceof LogMessage) {
            textMessage.setStringProperty(Sender.NJAMS_LOGID, ((LogMessage) msg).getLogId());
        }
        textMessage.setStringProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        textMessage.setStringProperty(Sender.NJAMS_MESSAGETYPE, messageType);
        textMessage.setStringProperty(Sender.NJAMS_PATH, msg.getPath());
        tryToSend(textMessage);
    }

    private void tryToSend(TextMessage textMessage) throws InterruptedException, JMSException {
        boolean sended = false;
        final int EXCEPTION_IDLE_TIME = 50;
        final int MAX_TRIES = 100;
        int tries = 0;

        do {
            try {
                producer.send(textMessage);
                sended = true;
            } catch (ResourceAllocationException ex) {
                if (discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS) {
                    LOG.debug("JMS Queue limit exceeded. Applying discard policy [{}]. Message discarded.",
                            discardPolicy);
                    break;
                }
                //Queue limit exceeded
                if (++tries >= MAX_TRIES) {
                    LOG.warn("Try to reconnect, because the MessageQueue hasn't got enough space after {} seconds.",
                            MAX_TRIES * EXCEPTION_IDLE_TIME);
                    throw ex;
                } else {
                    Thread.sleep(EXCEPTION_IDLE_TIME);
                }
            }
        } while (!sended);
    }

    /**
     * Close this Sender.
     */
    @Override

    public synchronized void close() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        if (producer != null) {
            try {
                producer.close();
                producer = null;
            } catch (JMSException ex) {
                LOG.warn("Unable to close producer", ex);
            }
        }
        if (session != null) {
            try {
                session.close();
                session = null;
            } catch (JMSException ex) {
                LOG.warn("Unable to close session", ex);
            }
        }
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (JMSException ex) {
                LOG.warn("Unable to close connection", ex);
            }
        }
    }

    @Override
    public synchronized void onException(JMSException exception) {
        onException(new NjamsSdkRuntimeException("JMS Exception", exception));
    }

    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
    }

    @Override
    protected void onException(NjamsSdkRuntimeException exception) {
        if (reconnector != null && reconnector.isAlive()) {
            return;
        }
        close();
        // reconnect
        reconnector = new Thread(() -> reconnect(exception));
        reconnector.setDaemon(true);
        reconnector.setName(String.format("%s-Sender-Reconnector-Thread", getName()));
        reconnector.start();
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    @Override
    public String[] librariesToCheck() {
        return new String[] { "javax.jms.Connection", "javax.jms.ConnectionFactory", "javax.jms.Destination",
                "javax" + ".jms" +
                        ".ExceptionListener",
                "javax.jms.Session", "javax.jms.JMSException", "javax.jms.MessageProducer",
                "javax.jms.Session", "javax.jms.TextMessage", "javax.naming.InitialContext",
                "javax.naming" + ".NameNotFoundException", "javax.naming.NamingException" };
    }
}
