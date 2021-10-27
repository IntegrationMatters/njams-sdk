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

package com.im.njams.sdk.communication.kafka;

import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.getHeader;
import static com.im.njams.sdk.communication.kafka.KafkaHeadersUtil.headersUpdater;

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Kafka implementation for a Receiver.
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class KafkaReceiver extends AbstractReceiver {

    private final Logger LOG = LoggerFactory.getLogger(KafkaReceiver.class);

    /** Name of the Kafka header storing the message's content type. Expected value is {@value #CONTENT_TYPE_JSON} */
    public static final String NJAMS_CONTENT = "NJAMS_CONTENT";
    /** Name of the Kafka header storing the request type */
    public static final String NJAMS_TYPE = "NJAMS_TYPE";
    /** Name of the Kafka header storing the receiver (client) path */
    public static final String NJAMS_RECEIVER = "NJAMS_RECEIVER";
    /** Name of the Kafka header storing a unique message ID */
    public static final String NJAMS_MESSAGE_ID = "NJAMS_MESSAGE_ID";
    /** Name of the Kafka header storing the ID of the request message to that a reply message belongs */
    public static final String NJAMS_REPLY_FOR = "NJAMS_REPLY_FOR";

    /** The value used with header {@value #NJAMS_TYPE} for reply messages */
    private static final String MESSAGE_TYPE_REPLY = "Reply";
    /** The value used with header {@value #NJAMS_CONTENT} for JSON content type (the only supported one) */
    public static final String CONTENT_TYPE_JSON = "json";

    private static final String COMMANDS_SUFFIX = ".commands";
    private static final String ID_REPLACE_PATTERN = "[^A-Za-z0-9_\\-\\.]";

    private KafkaProducer<String, String> producer;
    private CommandsConsumer commandConsumer;
    private Properties properties;
    private ObjectMapper mapper;
    private String topicName;
    private String clientId;

    /**
     * Initializes this Receiver via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMUNICATION_NAME}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#TOPIC_PREFIX}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMANDS_TOPIC}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        connectionStatus = ConnectionStatus.DISCONNECTED;
        mapper = JsonSerializerFactory.getDefaultMapper();
        final String clientPath = properties.getProperty(Settings.INTERNAL_PROPERTY_CLIENTPATH);
        clientId = getClientId(clientPath.substring(1, clientPath.length() - 1).replace('>', '_'));
        this.properties = filterKafkaProperties(properties, clientId);
        if (properties.containsKey(KafkaConstants.COMMANDS_TOPIC)) {
            topicName = properties.getProperty(KafkaConstants.COMMANDS_TOPIC);
        } else {
            topicName = properties.getProperty(KafkaConstants.TOPIC_PREFIX) + COMMANDS_SUFFIX;
        }
    }

    private static String getClientId(String path) {
        String id = "NJSDK_";
        int max = 255 - id.length();
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
            connectionStatus = ConnectionStatus.CONNECTING;

            tryToConnect();

            connectionStatus = ConnectionStatus.CONNECTED;
        }
    }

    /**
     * This method tries to create a CommandsListener, which is a separate Thread
     * for a Consumer, constantly polling. It throws an NjamsSdkRuntimeException if
     * any of the resources throws any exception.
     *
     * @param props the Properties that are used for connecting.
     */
    private void tryToConnect() {
        try {
            commandConsumer = new CommandsConsumer(properties, topicName, this);
            commandConsumer.start();
        } catch (Exception e) {
            closeAll();
            throw new NjamsSdkRuntimeException("Unable to start the Commands-Consumer-Thread", e);
        }
    }

    /**
     * This method sets the connectionStatus to DISCONNECTED and closes all
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

    @Override
    public void start() {
        if (validateTopics()) {
            super.start();
        }
    }

    private boolean validateTopics() {
        final Collection<String> foundTopics = KafkaUtil.testTopics(properties, topicName);
        LOG.debug("Found topics: {}", foundTopics);
        if (foundTopics.isEmpty()) {
            LOG.error("Commands topic [{}] not found. "
                    + "The client will not be able to process commands from nJAMS sever!", topicName);
            return false;
        }
        return true;
    }

    /**
     * This method stops the Kafka receiver, if its status is CONNECTED.
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
     * This method is called by the CommandsListener if a message arrives.
     *
     * @param msg the newly arrived Kafka message.
     */
    public void onMessage(ConsumerRecord<String, String> msg) {
        LOG.debug("Received message {}", msg);
        try {
            if (!isValidMessage(msg)) {
                return;
            }
            final String messageId = getHeader(msg, NJAMS_MESSAGE_ID);
            if (StringUtils.isBlank(messageId)) {
                LOG.error("Missing request ID in message: {}", msg);
                return;
            }
            final Instruction instruction = getInstruction(msg);
            if (instruction == null) {
                return;
            }
            LOG.debug("Handle message (id={}) {}", messageId, msg);
            onInstruction(instruction);
            sendReply(messageId, instruction);
        } catch (Exception e) {
            LOG.error("Failed to process instruction: {}", msg, e);
        }
    }

    protected boolean isValidMessage(ConsumerRecord<?, ?> msg) {
        if (msg == null) {
            return false;
        }
        if (StringUtils.isNotBlank(getHeader(msg, NJAMS_REPLY_FOR))) {
            // skip messages sent as a reply
            return false;
        }
        final String receiver = getHeader(msg, NJAMS_RECEIVER);
        if (StringUtils.isBlank(receiver) || !njams.getClientPath().equals(new Path(receiver))) {
            LOG.debug("Message is not for me!");
            return false;
        }
        if (!CONTENT_TYPE_JSON.equalsIgnoreCase(getHeader(msg, NJAMS_CONTENT))) {
            LOG.debug("Received non json instruction -> ignore");
            return false;
        }

        return true;
    }

    /**
     * This method tries to extract the Instruction out of the provided message. It
     * maps the Json string to an Instruction object.
     *
     * @param message the Json Message
     * @return the Instruction object that was extracted or null, if no valid
     *         instruction was found or it could be parsed to an instruction object.
     * @throws IOException
     */
    protected Instruction getInstruction(ConsumerRecord<String, String> message) throws IOException {
        return mapper.readValue(message.value(), Instruction.class);
    }

    /**
     * This method tries to reply the instructions response back to the sender. Send
     * a message to the Sender that is mentioned in the message. If a UUID is set in
     * the message, it will be forwarded as well.
     *
     * @param requestId The ID of the request to that this reply belongs
     * @param instruction the instruction that holds the response.
     */
    protected void sendReply(final String requestId, Instruction instruction) {
        try {
            final String responseId = UUID.randomUUID().toString();
            ProducerRecord<String, String> response =
                    new ProducerRecord<>(topicName, responseId, mapper.writeValueAsString(instruction));
            headersUpdater(response).
                    addHeader(NJAMS_MESSAGE_ID, responseId).
                    addHeader(NJAMS_REPLY_FOR, requestId).
                    addHeader(NJAMS_TYPE, MESSAGE_TYPE_REPLY).
                    addHeader(NJAMS_CONTENT, CONTENT_TYPE_JSON);

            synchronized (this) {
                if (producer == null) {
                    LOG.debug("Creating new Kafka producer.");
                    producer = new KafkaProducer<>(properties, new StringSerializer(), new StringSerializer());
                }
                LOG.debug("Sending reply for request {}: {}", requestId, response);
                producer.send(response);
                producer.flush();
            }

        } catch (Exception e) {
            LOG.error("Error while sending reply for {}", requestId, e);
        }
    }

    private Properties filterKafkaProperties(Properties properties, String client) {
        final Properties p = KafkaConstants.filterKafkaProperties(properties, client);
        if (!clientId.equals(p.getProperty(ConsumerConfig.CLIENT_ID_CONFIG))
                || !clientId.equals(p.getProperty(ConsumerConfig.GROUP_ID_CONFIG))) {
            LOG.warn("Default client- or group-ID is overridden by configuration.");
        }
        LOG.info("Kafka client.id={}, group.id={}", p.getProperty(ConsumerConfig.CLIENT_ID_CONFIG),
                p.getProperty(ConsumerConfig.GROUP_ID_CONFIG));
        return p;
    }

    /**
     * @return the name of this Receiver. (Kafka)
     */
    @Override
    public String getName() {
        return KafkaConstants.COMMUNICATION_NAME;
    }

    /**
     * Returns if the Commands-Response-Producer of this KafkaReceiver is running.
     */
    synchronized boolean hasRunningProducer() {
        return producer != null;
    }

    /**
     * Closes the Producer and sets it to null
     */
    synchronized void closeProducer() {
        if (producer != null) {
            LOG.debug("Closing reply producer.");
            producer.close();
            producer = null;
        }
    }
}
