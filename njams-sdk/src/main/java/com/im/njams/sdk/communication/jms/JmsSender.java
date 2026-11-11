/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import static com.im.njams.sdk.communication.MessageHeaders.*;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.ResourceAllocationException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.utils.StringUtils;

/**
 * JMS implementation for a Sender.
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public class JmsSender extends AbstractJmsSender implements ExceptionListener {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    private Connection connection = null;
    /** The JMS session this sender produces on. */
    protected Session session = null;
    /** Producer for the event destination, carrying log, trace and event messages. */
    protected MessageProducer eventProducer = null;
    /**
     * Producer for the project destination. The same instance as {@link #eventProducer} when no separate
     * project destination is configured.
     */
    protected MessageProducer projectProducer = null;

    /**
     * Resolves the configured {@link JmsFactory} and closes it again: the resolution itself is the probe that
     * tells whether the configured connection factory is a {@code jakarta.jms} one. {@link #connect()} resolves
     * its own factory as before.
     *
     * @param settings the settings the sender is initialized with
     * @throws TransportNotApplicableException if the configured factory is not a {@code jakarta.jms} implementation
     */
    @Override
    protected void resolveFactory(ClientSettings settings) {
        JmsFactoryProbe.probe(settings, LOG);
    }

    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        try (JmsFactory jmsFactory = JmsFactory.find(settings)) {
            setConnectionStatus(ConnectionStatus.CONNECTING);
            ConnectionFactory factory = jmsFactory.createConnectionFactory();
            if (StringUtils.isNotBlank(settings.getProperty(NjamsSettings.PROPERTY_JMS_USERNAME))
                && StringUtils.isNotBlank(settings.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD))) {
                connection = factory.createConnection(settings.getProperty(NjamsSettings.PROPERTY_JMS_USERNAME),
                    settings.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD));
            } else {
                connection = factory.createConnection();
            }
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            createProducers(jmsFactory, session);

            connection.setExceptionListener(this);
            setConnectionStatus(ConnectionStatus.CONNECTED);
        } catch (Exception e) {
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
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
        }

    }

    private void createProducers(JmsFactory jmsFactory, Session session) throws NamingException, JMSException {
        final String prefix = destinationPrefix();
        eventProducer = createProducer(jmsFactory, session, prefix + ".event");
        if (isUseProjectQueue()) {
            // separate one for project/trace messages
            projectProducer = createProducer(jmsFactory, session, prefix + ".project");
        } else {
            // same for all messages
            projectProducer = eventProducer;
        }

    }

    private MessageProducer createProducer(JmsFactory jmsFactory, Session session, String destinationName)
        throws NamingException, JMSException {
        Queue destination = jmsFactory.createQueue(session, destinationName);
        final MessageProducer messageProducer = session.createProducer(destination);
        messageProducer.setDeliveryMode(resolveDeliveryMode());
        return messageProducer;
    }

    /**
     * Send the given LogMessage to the specified JMS.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(LogMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        try {
            sendMessage(eventProducer, msg, MESSAGETYPE_EVENT, clientSessionId);
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
    protected void send(ProjectMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        try {
            sendMessage(projectProducer, msg, MESSAGETYPE_PROJECT, clientSessionId);
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
    protected void send(TraceMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        try {
            sendMessage(projectProducer, msg, MESSAGETYPE_TRACE, clientSessionId);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send TraceMessage", e);
        }
    }

    /**
     * Sends the given message through the given producer.
     *
     * @param producer the producer to send with
     * @param msg the message to send
     * @param messageType the nJAMS message type header value
     * @param clientSessionId the client session id header value
     * @throws Exception if the message cannot be sent
     */
    protected void sendMessage(MessageProducer producer, CommonMessage msg, String messageType,
        String clientSessionId) throws Exception {
        sendMessage((Object) producer, msg, messageType, clientSessionId);
    }

    @Override
    protected Object buildMessage(String logId, String path, String msg, String messageType,
        String clientSessionId) throws JMSException {
        final TextMessage textMessage = session.createTextMessage(msg);
        if (logId != null) {
            textMessage.setStringProperty(NJAMS_LOGID_HEADER, logId);
        }
        textMessage.setStringProperty(NJAMS_MESSAGEVERSION_HEADER, MessageVersion.V4.toString());
        textMessage.setStringProperty(NJAMS_MESSAGETYPE_HEADER, messageType);
        textMessage.setStringProperty(NJAMS_PATH_HEADER, path);
        textMessage.setStringProperty(NJAMS_CLIENTID_HEADER, clientSessionId);
        return textMessage;
    }

    @Override
    protected void setProperty(Object message, String key, String value) {
        try {
            ((TextMessage) message).setStringProperty(key, value);
        } catch (JMSException e) {
            LOG.error("Failed to set property: {}", key, e);
        }
    }

    @Override
    protected void tryToSend(Object producer, Object message) throws Exception {
        sendWithRetry(() -> ((MessageProducer) producer).send((TextMessage) message));
    }

    @Override
    protected String describeDestination(Object producer) {
        try {
            return String.valueOf(((MessageProducer) producer).getDestination());
        } catch (JMSException e) {
            return "<unknown destination>";
        }
    }

    @Override
    protected Class<?> congestionExceptionType() {
        return ResourceAllocationException.class;
    }

    /**
     * Close this Sender.
     */
    @Override
    public synchronized void close() {
        if (!isConnected()) {
            return;
        }
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
        if (eventProducer != null) {
            try {
                eventProducer.close();
                eventProducer = null;
                if (!isUseProjectQueue()) {
                    projectProducer = null;
                }
            } catch (JMSException ex) {
                LOG.warn("Unable to close event producer", ex);
            }
        }
        if (isUseProjectQueue() && projectProducer != null) {
            try {
                projectProducer.close();
                projectProducer = null;
            } catch (JMSException ex) {
                LOG.warn("Unable to close project producer", ex);
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
        notifyConnectionFailure(new NjamsSdkRuntimeException("JMS Exception", exception));
    }
}
