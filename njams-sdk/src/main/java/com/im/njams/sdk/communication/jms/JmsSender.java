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

import static com.im.njams.sdk.communication.MessageHeaders.*;

import java.util.List;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
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

import org.apache.kafka.common.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.DiscardMonitor;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.communication.NjamsConnectionFactory;
import com.im.njams.sdk.communication.SplitSupport;
import com.im.njams.sdk.settings.PropertyUtil;
import com.im.njams.sdk.utils.ClasspathValidator;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * JMS implementation for a Sender.
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public class JmsSender extends AbstractSender implements ExceptionListener, ClasspathValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    /**
     * Name for the JMS communication implementation.
     */
    public static final String COMMUNICATION_NAME = "JMS";

    private Connection connection;
    protected Session session;
    protected MessageProducer producer;
    private SplitSupport splitSupport;

    /**
     * Initializes this Sender via the given Properties.
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        super.init(properties);
        splitSupport = new SplitSupport(properties);
        LOG.debug("Initialized sender {}", getName());
    }

    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        InitialContext context = null;
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            context = new InitialContext(PropertyUtil.filterAndCut(properties, NjamsSettings.PROPERTY_JMS_PREFIX));
            ConnectionFactory factory = NjamsConnectionFactory.getFactory(context, properties);
            if (StringUtils.isNotBlank(properties.getProperty(NjamsSettings.PROPERTY_JMS_USERNAME))
                && StringUtils.isNotBlank(properties.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD))) {
                connection = factory.createConnection(properties.getProperty(NjamsSettings.PROPERTY_JMS_USERNAME),
                    properties.getProperty(NjamsSettings.PROPERTY_JMS_PASSWORD));
            } else {
                connection = factory.createConnection();
            }
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Destination destination = null;
            String destinationName = properties.getProperty(NjamsSettings.PROPERTY_JMS_DESTINATION) + ".event";
            try {
                destination = (Destination) context.lookup(destinationName);
            } catch (NameNotFoundException e) {
                destination = session.createQueue(destinationName);
            }
            producer = session.createProducer(destination);
            String deliveryMode = properties.getProperty(NjamsSettings.PROPERTY_JMS_DELIVERY_MODE);
            if ("NON_PERSISTENT".equalsIgnoreCase(deliveryMode)) {
                LOG.debug("Set JMS delivery mode to NON_PERSISTENT.");
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            }
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
    protected void send(LogMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        try {
            sendMessage(msg, MESSAGETYPE_EVENT, clientSessionId);
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
            sendMessage(msg, MESSAGETYPE_PROJECT, clientSessionId);
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
            sendMessage(msg, MESSAGETYPE_TRACE, clientSessionId);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send TraceMessage", e);
        }
    }

    String serialize(CommonMessage msg) {
        return JsonUtils.serialize(msg);
    }

    protected void sendMessage(CommonMessage msg, String messageType, String clientSessionId)
        throws JMSException, InterruptedException {
        final String data = serialize(msg);
        if (splitSupport.isSplitting()) {
            sendChunks(msg, data, messageType, clientSessionId);
        } else {
            final String logId;
            if (msg instanceof LogMessage) {
                logId = ((LogMessage) msg).getLogId();
            } else {
                logId = null;
            }

            final TextMessage textMessage = buildMessage(logId, msg.getPath(), data, messageType, clientSessionId);
            tryToSend(textMessage);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Send {} for {} to {}:\n{}", msg.getClass().getSimpleName(), msg.getPath(),
                producer.getDestination(), data);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Send {} for {} to {}", msg.getClass().getSimpleName(), msg.getPath(),
                producer.getDestination());
        }
    }

    private void sendChunks(CommonMessage msg, String data, String messageType, String clientSessionId)
        throws JMSException, InterruptedException {
        final List<String> chunks = splitSupport.splitData(data);
        if (chunks.isEmpty()) {
            return;
        }
        final String messageKey;
        final String logId;
        if (msg instanceof LogMessage) {
            logId = ((LogMessage) msg).getLogId();
            messageKey = logId;
        } else {
            if (chunks.size() > 1) {
                // ensure same key for all chunks
                messageKey = Uuid.randomUuid().toString();
            } else {
                messageKey = null;
            }
            logId = null;
        }
        for (int i = 0; i < chunks.size(); i++) {
            final TextMessage textMessage = buildMessage(logId, msg.getPath(), data, messageType, clientSessionId);
            splitSupport.addChunkHeaders((k, v) -> setProperty(textMessage, k, v), i, chunks.size(), messageKey);
            tryToSend(textMessage);
        }
    }

    private TextMessage buildMessage(String logId, String path, String msg, String messageType,
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

    private void setProperty(TextMessage message, String key, String value) {
        try {
            message.setStringProperty(key, value);
        } catch (JMSException e) {
            LOG.error("Failed to set property: {}", key, e);
        }
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
                    DiscardMonitor.discard();
                    break;
                }
                //Queue limit exceeded
                if (++tries >= MAX_TRIES) {
                    LOG.warn("Try to reconnect, because the MessageQueue hasn't got enough space after {} seconds.",
                        MAX_TRIES * EXCEPTION_IDLE_TIME);
                    throw ex;
                }
                Thread.sleep(EXCEPTION_IDLE_TIME);
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
        return COMMUNICATION_NAME;
    }

    /**
     * This method gets all libraries that need to be checked.
     *
     * @return an array of Strings of fully qualified class names.
     */
    @Override
    public String[] librariesToCheck() {
        return new String[] { "javax.jms.Connection", "javax.jms.ConnectionFactory", "javax.jms.Destination",
            "javax.jms.ExceptionListener",
            "javax.jms.Session", "javax.jms.JMSException",
            "javax.jms.MessageProducer",
            "javax.jms.Session", "javax.jms.TextMessage", "javax.naming.InitialContext",
            "javax.naming" + ".NameNotFoundException", "javax.naming.NamingException" };
    }
}
