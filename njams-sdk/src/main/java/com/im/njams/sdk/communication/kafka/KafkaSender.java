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

import static com.im.njams.sdk.communication.MessageHeaders.*;
import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.headersUpdater;
import static com.im.njams.sdk.utils.PropertyUtil.getPropertyInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.serialization.StringSerializer;
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
import com.im.njams.sdk.communication.SplitSupport;
import com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.HeadersUpdater;
import com.im.njams.sdk.communication.kafka.KafkaUtil.ClientType;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Kafka implementation for a Sender.
 *
 * @author sfaiz
 */
public class KafkaSender extends AbstractSender {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaSender.class);

    /** Kafka's internal default for a producer's message size limit (1MB). */
    private static final int MAX_MESSAGE_SIZE_DEFAULT = 1048576;
    /** Some overhead that is subtracted from Kafka's producer message size limit, if used. */
    public static final int HEADERS_OVERHEAD = 4096;

    private static final String PROJECT_SUFFIX = ".project";
    private static final String EVENT_SUFFIX = ".event";

    private KafkaProducer<String, String> producer;
    private String topicEvent;
    private String topicProject;
    private Properties kafkaProperties;
    private SplitSupport splitSupport;

    private int requestTimeoutMs = 6000;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * See all valid properties in KafkaConstants
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(final Properties properties) {
        super.init(properties);
        kafkaProperties = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
        if (discardPolicy != DiscardPolicy.NONE && !kafkaProperties.containsKey(ProducerConfig.RETRIES_CONFIG)) {
            // disable internal retries, if policy is set to discard, unless explicitly specified
            kafkaProperties.setProperty(ProducerConfig.RETRIES_CONFIG, "0");
        }

        String topicPrefix = properties.getProperty(NjamsSettings.PROPERTY_KAFKA_TOPIC_PREFIX);
        if (StringUtils.isBlank(topicPrefix)) {
            LOG.warn("Property {} is not set. Using '{}' as default.", NjamsSettings.PROPERTY_KAFKA_TOPIC_PREFIX,
                KafkaConstants.DEFAULT_TOPIC_PREFIX);
            topicPrefix = KafkaConstants.DEFAULT_TOPIC_PREFIX;
        }
        topicEvent = topicPrefix + EVENT_SUFFIX;
        topicProject = topicPrefix + PROJECT_SUFFIX;
        initMaxMessageSizeAndTimeout();
        splitSupport = new SplitSupport(properties, getProducerLimit(properties));
        LOG.debug("Initialized sender {}", KafkaConstants.COMMUNICATION_NAME);
    }

    /**
     * Get Kafka's producer setting or use its default.
     * See also {@link NjamsSettings#PROPERTY_MAX_MESSAGE_SIZE}
     * @param properties
     * @return
     */
    private int getProducerLimit(final Properties properties) {
        final Properties kafka = KafkaUtil.filterKafkaProperties(properties, ClientType.PRODUCER);
        final int kafkaLimit = getPropertyInt(kafka, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, MAX_MESSAGE_SIZE_DEFAULT);
        return kafkaLimit - HEADERS_OVERHEAD;
    }

    private void initMaxMessageSizeAndTimeout() {

        // set request timeout according to producer config +1 second
        final int i = getPropertyInt(kafkaProperties, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        if (i > 0) {
            requestTimeoutMs = i + 1000;
        } else {
            int r = getPropertyInt(kafkaProperties, ProducerConfig.RETRIES_CONFIG, 2147483647);
            int b = getPropertyInt(kafkaProperties, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
            if (r > 0 && b > 0) {
                requestTimeoutMs = r * b + 1000;
            }
        }
        requestTimeoutMs = Math.min(6000, requestTimeoutMs);
        LOG.debug("Request timeout {}ms", requestTimeoutMs);

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
        final Collection<String> requiredTopics = new ArrayList<>(Arrays.asList(topicEvent, topicProject));
        final Collection<String> foundTopics =
            KafkaUtil.testTopics(properties, requiredTopics.toArray(new String[requiredTopics.size()]));
        LOG.debug("Found topics: {}", foundTopics);
        requiredTopics.removeAll(foundTopics);
        if (!requiredTopics.isEmpty()) {
            throw new NjamsSdkRuntimeException("The following required Kafka topics have not been found: "
                + requiredTopics);
        }
    }

    /**
     * Send the given LogMessage to the specified Kafka.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(final LogMessage msg, String clientSessionId) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicEvent, MESSAGETYPE_EVENT, data, clientSessionId);
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
    protected void send(final ProjectMessage msg, String clientSessionId) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicProject, MESSAGETYPE_PROJECT, data, clientSessionId);
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
    protected void send(final TraceMessage msg, String clientSessionId) {
        try {
            final String data = JsonUtils.serialize(msg);
            sendMessage(msg, topicProject, MESSAGETYPE_TRACE, data, clientSessionId);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send TraceMessage {} to {}:\n{}", msg.getPath(), topicProject, data);
            } else {
                LOG.debug("Send TraceMessage for {} to {}", msg.getPath(), topicProject);
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
     * @throws Exception
     */
    private void sendMessage(final CommonMessage msg, final String topic, final String messageType, final String data,
        String clientSessionId)
        throws Exception {

        // for Kafka, there is always a max message size limit that may cause message fragmentation
        try {
            for (ProducerRecord<String, String> record : splitMessage(msg, topic, messageType, data, clientSessionId)) {
                tryToSend(record);
            }
        } catch (Throwable e) {
            LOG.error("Failed to prepare '{}' message", messageType, e);
            throw e;
        }
    }

    List<ProducerRecord<String, String>> splitMessage(final CommonMessage msg, final String topic,
        final String messageType, final String data, String clientSessionId) {

        List<String> slices = splitSupport.splitData(data);
        if (slices.isEmpty()) {
            return Collections.emptyList();
        }
        final String recordKey;
        final String logId;
        if (msg instanceof LogMessage) {
            logId = ((LogMessage) msg).getLogId();
            recordKey = logId;
        } else {
            if (slices.size() > 1) {
                // ensure same key for all chunks
                recordKey = Uuid.randomUuid().toString();
            } else {
                recordKey = null;
            }
            logId = null;
        }

        List<ProducerRecord<String, String>> chunks = null;
        for (int i = 0; i < slices.size(); i++) {
            final ProducerRecord<String, String> record = new ProducerRecord<>(topic, recordKey, slices.get(i));
            final HeadersUpdater headers = headersUpdater(record);
            headers.addHeader(NJAMS_MESSAGEVERSION_HEADER, MessageVersion.V4.toString())
                .addHeader(NJAMS_MESSAGETYPE_HEADER, messageType)
                .addHeader(NJAMS_CLIENTID_HEADER, clientSessionId);
            if (StringUtils.isNotBlank(logId)) {
                headers.addHeader(NJAMS_LOGID_HEADER, logId);
            }
            if (StringUtils.isNotBlank(msg.getPath())) {
                headers.addHeader(NJAMS_PATH_HEADER, msg.getPath());
            }
            if (slices.size() <= 1) {
                return Collections.singletonList(record);
            }
            splitSupport.addChunkHeaders(headers::addHeader, i, slices.size(), recordKey);
            if (chunks == null) {
                chunks = new ArrayList<>();
            }
            chunks.add(record);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} for path {} split into {} chunks.", msg.getClass().getSimpleName(), msg.getPath(),
                chunks == null ? "null" : chunks.size());
        }
        return chunks;
    }

    /**
     * Relies on Kafka's internal retry which is controlled by according producer settings.
     *
     * @param record
     * @throws Exception
     */
    private void tryToSend(final ProducerRecord<String, String> record) throws Exception {
        long start = System.currentTimeMillis();
        try {
            final Future<RecordMetadata> future = producer.send(record);
            final RecordMetadata result = future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send record result: {} after {}ms\n{}", result, System.currentTimeMillis() - start,
                    record);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Send record result: {} after {}ms", result, System.currentTimeMillis() - start);
            }

        } catch (KafkaException | IllegalStateException | ExecutionException | TimeoutException e) {
            LOG.debug("Failed to send record", e);
            Exception cause = getAsyncCause(e);
            if (cause instanceof RecordTooLargeException) {
                // if splitting is enabled, this can still be caused by misconfiguration!
                LOG.warn("Discarding message that is too large: {}", cause.toString());
                DiscardMonitor.discard();
            }
            if (discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS) {
                LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                DiscardMonitor.discard();
            }
            LOG.warn("Try to reconnect, because the topic couldn't be reached after {} milliseconds.",
                System.currentTimeMillis() - start);
            throw cause;
        }
    }

    /**
     * Exceptions from Kafka are wrapped into {@link ExecutionException} due to async behavior of the
     * {@link KafkaProducer#send(ProducerRecord)} methods.
     *
     * @param e
     * @return
     */
    private Exception getAsyncCause(Exception e) {
        if (e instanceof ExecutionException && e.getCause() instanceof Exception) {
            return (Exception) e.getCause();
        }
        return e;
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
