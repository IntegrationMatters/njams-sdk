/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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

package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.MessageHeaders.*;
import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.getHeader;
import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.headersUpdater;
import static com.im.njams.sdk.communication.kafka.KafkaUtil.filterKafkaProperties;
import static com.im.njams.sdk.communication.kafka.KafkaUtil.getProducerLimit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.fragments.KafkaChunkAssembly;
import com.im.njams.sdk.communication.fragments.RawMessage;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.HeadersUpdater;
import com.im.njams.sdk.communication.kafka.KafkaUtil.ClientType;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Kafka implementation for a Receiver.
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaReceiver extends AbstractReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaReceiver.class);

    private static final String GROUP_PREFIX = "njsdk_";
    private static final String COMMANDS_SUFFIX = ".commands";
    private static final String ID_REPLACE_PATTERN = "[^A-Za-z0-9_\\-\\.]";

    private KafkaProducer<String, String> producer;
    private CommandsConsumer commandConsumer;
    private Properties njamsProperties;
    private ObjectMapper mapper;
    private String topicName = null;
    private String kafkaClientId;

    private SplitSupport splitSupport = null;
    protected final KafkaChunkAssembly chunkAssembly = new KafkaChunkAssembly();

    /**
     * Initializes this receiver via the given properties.
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(final Properties properties) {
        njamsProperties = properties;
        connectionStatus = ConnectionStatus.DISCONNECTED;
        mapper = JsonSerializerFactory.getFastMapper();
        final String clientPath = properties.getProperty(Settings.INTERNAL_PROPERTY_CLIENTPATH);
        kafkaClientId = getClientId(clientPath.substring(1, clientPath.length() - 1).replace('>', '_'));
        String prefix = properties.getProperty(NjamsSettings.PROPERTY_KAFKA_TOPIC_PREFIX);
        if (StringUtils.isBlank(prefix)) {
            LOG.warn("Property {} is not set. Using '{}' as default.", NjamsSettings.PROPERTY_KAFKA_TOPIC_PREFIX,
                KafkaConstants.DEFAULT_TOPIC_PREFIX);
            prefix = KafkaConstants.DEFAULT_TOPIC_PREFIX;
        }
        topicName = prefix + COMMANDS_SUFFIX;

        splitSupport = new SplitSupport(properties, getProducerLimit(properties));
        if (splitSupport.isSplitting()) {
            LOG.debug("Init with message fragmentation support (max={} bytes).", splitSupport.getMaxMessageSize());
        }
    }

    private static String getClientId(final String path) {
        String id = GROUP_PREFIX;
        final int max = 255 - id.length();
        if (path.length() > max) {
            id += path.substring(0, max - 9) + "_" + Integer.toHexString(path.hashCode());
        } else {
            id += path;
        }
        return id.replaceAll(ID_REPLACE_PATTERN, "_");
    }

    /**
     * This method is called when the receiver has to connect. It can't be started
     * if init(..) hasn't been called beforehand.
     */
    @Override
    public synchronized void connect() {
        if (!isConnected()) {
            LOG.debug("Connect: Subscribe KafkaReceiver to topic {}", topicName);
            connectionStatus = ConnectionStatus.CONNECTING;
            validateTopics();
            tryToConnect();

            connectionStatus = ConnectionStatus.CONNECTED;
        }
    }

    /**
     * This method tries to create a {@link CommandsConsumer}, which is a separate thread
     * for a consumer, constantly polling.
     *
     * @throws NjamsSdkRuntimeException if any of the resources throws any exception.
     */
    private void tryToConnect() {
        LOG.debug("Try subscribing KafkaReceiver to topic {}", topicName);
        try {
            commandConsumer = new CommandsConsumer(njamsProperties, topicName, kafkaClientId, this);
            commandConsumer.start();
        } catch (final Exception e) {
            closeAll();
            LOG.debug("Try subscribing KafkaReceiver to topic {} failed: {}", topicName, e.toString());
            throw new NjamsSdkRuntimeException("Unable to start the Commands-Consumer-Thread", e);
        }
    }

    /**
     * This method sets the connectionStatus to {@link ConnectionStatus#DISCONNECTED} and closes all
     * resources
     */
    private synchronized void closeAll() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        if (commandConsumer != null) {
            commandConsumer.doStop();
            commandConsumer = null;
        }
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }

    private void validateTopics() {
        final Collection<String> foundTopics = KafkaUtil.testTopics(njamsProperties, topicName);
        LOG.debug("Found topics: {}", foundTopics);
        if (foundTopics.isEmpty()) {
            throw new NjamsSdkRuntimeException("Commands topic [" + topicName + "] not found.");
        }
    }

    /**
     * This method stops the Kafka receiver, if its status is {@link ConnectionStatus#CONNECTED}
     */
    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        closeAll();
        LOG.info("{} has been stopped successfully.", getClass().getSimpleName());
    }

    /**
     * This method is called by the {@link CommandsConsumer} if a message arrives.
     *
     * @param msg the newly arrived Kafka message.
     */
    public void onMessage(final ConsumerRecord<String, String> msg) {
        LOG.debug("Received message {}", msg);
        try {
            if (!isValidMessage(msg)) {
                return;
            }
            final RawMessage raw = chunkAssembly.resolve(msg);
            if (raw == null) {
                LOG.debug("Received partial message");
                return;
            }
            final String messageId = raw.getHeader(NJAMS_MESSAGE_ID_HEADER);
            if (StringUtils.isBlank(messageId)) {
                LOG.error("Missing request ID in message: {}", msg);
                return;
            }

            final Instruction instruction = parseInstruction(raw);
            if (instruction == null || suppressGetRequestHandlerInstruction(instruction, njams)) {
                return;
            }

            LOG.debug("Handle message (id={}) {}", messageId, raw);
            onInstruction(instruction);
            sendReply(messageId, instruction, njams.getCommunicationSessionId());

        } catch (final Exception e) {
            LOG.error("Failed to process instruction: {}", msg, e);
        }
    }

    protected boolean isValidMessage(final ConsumerRecord<?, ?> msg) {
        if (msg == null || StringUtils.isNotBlank(getHeader(msg, NJAMS_REPLY_FOR_HEADER))) {
            // skip messages sent as a reply
            return false;
        }
        final String receiver = getHeader(msg, NJAMS_RECEIVER_HEADER);
        if (StringUtils.isBlank(receiver) || !njams.getClientPath().equals(new Path(receiver))) {
            LOG.debug("Message is not for me!");
            return false;
        }
        if (!CONTENT_TYPE_JSON.equalsIgnoreCase(getHeader(msg, NJAMS_CONTENT_HEADER))) {
            LOG.debug("Received non json instruction -> ignore");
            return false;
        }

        final String clientId = getHeader(msg, NJAMS_CLIENTID_HEADER);
        if (clientId != null && !njams.getCommunicationSessionId().equals(clientId)) {
            LOG.debug("Message is not for me! ClientId in Message is: {} but this nJAMS Client has Id: {}",
                clientId, njams.getCommunicationSessionId());
            return false;
        }

        return true;
    }

    /**
     * This method tries to reply the instructions response back to nJAMS server.
     *
     * @param requestId   The ID of the request to that this reply belongs
     * @param instruction the instruction that holds the response.
     * @param clientId
     */
    protected void sendReply(final String requestId, final Instruction instruction, String clientId) {
        final String responseId = UUID.randomUUID().toString();
        final Map<String, String> headers = new TreeMap<>();
        headers.put(NJAMS_MESSAGE_ID_HEADER, responseId);
        headers.put(NJAMS_REPLY_FOR_HEADER, requestId);
        headers.put(NJAMS_RECEIVER_HEADER, RECEIVER_SERVER);
        headers.put(NJAMS_TYPE_HEADER, COMMAND_TYPE_REPLY);
        headers.put(NJAMS_CONTENT_HEADER, CONTENT_TYPE_JSON);
        if (clientId != null) {
            headers.put(NJAMS_CLIENTID_HEADER, clientId);
        }
        final String data;
        try {
            data = mapper.writeValueAsString(instruction);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize reply for request {}", requestId, e);
            return;
        }
        if (splitSupport.isSplitting()) {
            final List<String> chunks = splitSupport.splitData(data);
            for (int i = 0; i < chunks.size(); i++) {
                sendReply(headers, responseId, chunks.get(i), i, chunks.size());
            }
        } else {
            sendReply(headers, responseId, data, 1, 1);
        }
    }

    protected void sendReply(final Map<String, String> headers, String responseId, final String body, int chunkNo,
        int totalChunks) {
        try {
            final ProducerRecord<String, String> response = new ProducerRecord<>(topicName, responseId, body);
            final HeadersUpdater headersUpdater = headersUpdater(response).addAllHeaders(headers);
            splitSupport.addChunkHeaders(headersUpdater::addHeader, chunkNo, totalChunks, responseId);

            synchronized (this) {
                if (producer == null) {
                    LOG.debug("Creating new Kafka producer.");
                    producer =
                        new KafkaProducer<>(
                            filterKafkaProperties(njamsProperties, ClientType.PRODUCER, kafkaClientId),
                            new StringSerializer(), new StringSerializer());
                }
                LOG.debug("Sending reply {} (part {}/{}): {}", responseId, chunkNo, totalChunks, response);
                producer.send(response);
                if (chunkNo >= totalChunks) {
                    producer.flush();
                }
            }

        } catch (final Exception e) {
            LOG.error("Error while sending reply {}", responseId, e);
            closeProducer();
        }
    }

    /**
     * @return the name of this Receiver: {@value KafkaConstants#COMMUNICATION_NAME}
     */
    @Override
    public String getName() {
        return KafkaConstants.COMMUNICATION_NAME;
    }

    /**
     * Returns whether this instance currently has an active reply producer.
     */
    synchronized boolean hasRunningProducer() {
        return producer != null;
    }

    /**
     * Closes the producer and sets it to null
     */
    synchronized void closeProducer() {
        if (producer != null) {
            LOG.debug("Closing reply producer.");
            producer.close();
            producer = null;
        }
    }
}
