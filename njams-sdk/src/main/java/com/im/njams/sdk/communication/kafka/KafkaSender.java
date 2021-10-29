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

package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.headersUpdater;

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.DiscardMonitor;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.kafka.KafkaUtil.ClientType;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Kafka implementation for a Sender.
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaSender extends AbstractSender {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaSender.class);
    private static final String PROJECT_SUFFIX = ".project";
    private static final String EVENT_SUFFIX = ".event";
    private static final int EXCEPTION_IDLE_TIME = 50;
    private static final int MAX_TRIES = 100;

    private KafkaProducer<String, String> producer;
    private String topicEvent;
    private String topicProject;
    private Properties kafkaProperties;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMUNICATION_NAME}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#TOPIC_PREFIX}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMANDS_TOPIC}
     * </ul>
     * See all valid properties in KafkaConstants
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(final Properties properties) {
        super.init(properties);
        kafkaProperties = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
        String topicPrefix = properties.getProperty(KafkaConstants.TOPIC_PREFIX);
        if (StringUtils.isBlank(topicPrefix)) {
            LOG.warn("Property {} is not set. Using '{}' as default.", KafkaConstants.TOPIC_PREFIX,
                    KafkaConstants.DEFAULT_TOPIC_PREFIX);
            topicPrefix = KafkaConstants.DEFAULT_TOPIC_PREFIX;
        }
        topicEvent = topicPrefix + EVENT_SUFFIX;
        topicProject = topicPrefix + PROJECT_SUFFIX;
        try {
            connect();
            LOG.debug("Initialized sender {}", KafkaConstants.COMMUNICATION_NAME);
        } catch (final NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize sender {}\n", KafkaConstants.COMMUNICATION_NAME, e);
        }
    }

    /**
     * Create the KafkaProducer for Events.
     */
    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }
        try {
            validateTopics();
            connectionStatus = ConnectionStatus.CONNECTING;
            producer = new KafkaProducer<>(kafkaProperties, new StringSerializer(), new StringSerializer());
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            if (producer != null) {
                producer.close();
                producer = null;
            }

            throw new NjamsSdkRuntimeException("Unable to connect", e);
        }
    }

    private void validateTopics() {
        final String[] topics = new String[] { topicEvent, topicProject };
        final Collection<String> foundTopics = KafkaUtil.testTopics(properties, topics);
        LOG.debug("Found topics: {}", foundTopics);
        if (foundTopics.size() < topics.length) {
            final Collection<String> missing = Arrays.asList(topics);
            missing.removeAll(foundTopics);
            throw new NjamsSdkRuntimeException("The following required Kafka topics have not been found: " + missing);
        }
    }

    /**
     * Send the given LogMessage to the specified Kafka.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(final LogMessage msg) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicEvent, Sender.NJAMS_MESSAGETYPE_EVENT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send LogMessage {} to {}:\n{}", msg.getPath(), topicEvent, data);
            } else {
                LOG.debug("Send Logmessage for {} to {}", msg.getPath(), topicEvent);
            }
        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send LogMessage", e);
        }
    }

    /**
     * Send the given ProjectMessage to the specified Kafka.
     *
     * @param msg the Projectmessage to send
     */
    @Override
    protected void send(final ProjectMessage msg) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicProject, Sender.NJAMS_MESSAGETYPE_PROJECT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send ProjectMessage {} to {}:\n{}", msg.getPath(), topicProject, data);
            } else {
                LOG.debug("Send ProjectMessage for {} to {}", msg.getPath(), topicProject);
            }
        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send ProjectMessage", e);
        }
    }

    /**
     * Send the given Tracemessage to the specified Kafka.
     *
     * @param msg the Tracemessage to send
     */
    @Override
    protected void send(final TraceMessage msg) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicEvent, Sender.NJAMS_MESSAGETYPE_TRACE, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send TraceMessage {} to {}:\n{}", msg.getPath(), topicEvent, data);
            } else {
                LOG.debug("Send TraceMessage for {} to {}", msg.getPath(), topicEvent);
            }
        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send TraceMessage", e);
        }
    }

    /**
     * Builds Headers and creates the ProducerRecord.
     *
     * @param msg
     * @param messageType
     * @param data
     * @throws InterruptedException
     */
    private void sendMessage(final CommonMessage msg, final String topic, final String messageType, final String data)
            throws InterruptedException {
        final ProducerRecord<String, String> record;
        final String id;
        if (msg instanceof LogMessage) {
            id = ((LogMessage) msg).getLogId();
            record = new ProducerRecord<>(topic, id, data);
        } else {
            id = null;
            record = new ProducerRecord<>(topic, data);
        }
        headersUpdater(record).
                addHeader(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString()).
                addHeader(Sender.NJAMS_MESSAGETYPE, messageType).
                addHeader(Sender.NJAMS_LOGID, id, (k, v) -> StringUtils.isNotBlank(v)).
                addHeader(Sender.NJAMS_PATH, msg.getPath(), (k, v) -> StringUtils.isNotBlank(v));
        try {
            tryToSend(record);
        } catch (final Exception e) {
            // TODO
            LOG.error("Failed to send.", e);
            throw e;
        }
    }

    /**
     * Try to send and catches Errors
     *
     * @param message
     * @throws InterruptedException
     */
    private void tryToSend(final ProducerRecord<String, String> message) throws InterruptedException {
        boolean sent = false;

        int tries = 0;

        do {
            try {
                producer.send(message);
                LOG.trace("Sent message {}", message);
                sent = true;
            } catch (KafkaException | IllegalStateException e) {
                if (discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS) {
                    LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                    DiscardMonitor.discard();
                    break;
                }
                if (++tries >= MAX_TRIES) {
                    LOG.warn("Try to reconnect, because the topic couldn't be reached after {} seconds.",
                            MAX_TRIES * EXCEPTION_IDLE_TIME);
                    throw e;
                } else {
                    Thread.sleep(EXCEPTION_IDLE_TIME);
                }
            }
        } while (!sent);
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
            } catch (KafkaException | IllegalArgumentException ex) {
                LOG.warn("Unable to close connection", ex);
            }
        }
    }

    /**
     * @return the name of this Sender. (Kafka)
     */
    @Override
    public String getName() {
        return KafkaConstants.COMMUNICATION_NAME;
    }
}
