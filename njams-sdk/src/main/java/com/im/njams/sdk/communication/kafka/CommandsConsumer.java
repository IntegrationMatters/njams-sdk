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

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.kafka.KafkaUtil.ClientType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A thread for a KafkaConsumer, that constantly needs to check if there are new commands in its Kafka topic
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class CommandsConsumer extends Thread {

    private static final int DEFAULT_PRODUCER_IDLE_TIME_MS = 30000;

    private static final Logger LOG = LoggerFactory.getLogger(CommandsConsumer.class);

    private KafkaConsumer<String, String> consumer;
    private long lastMessageProcessingTimestamp;
    private final long idleProducerTimeout;
    private final KafkaReceiver receiver;
    private boolean doStop = false;

    /**
     * This constructor saves the given properties and creates a KafkaConsumer
     *
     * @param properties the nJAMS properties that are used for connecting
     * @param topic      topic to subscribe to
     * @param clientId   The client id to be used for the consumer
     * @param receiver   the {@link KafkaReceiver} that uses this consumer instance
     */
    protected CommandsConsumer(final Properties properties, final String topic, final String clientId,
                               final KafkaReceiver receiver) {
        super("kafka_commands_consumer");
        idleProducerTimeout = getProducerIdleTime(properties);
        this.receiver = receiver;

        final Properties kafkaProperties = KafkaUtil.filterKafkaProperties(properties, ClientType.CONSUMER, clientId);
        // transient group; we are only interested in new messages
        kafkaProperties.remove(ConsumerConfig.GROUP_ID_CONFIG);
        try {
            consumer = new KafkaConsumer<>(kafkaProperties, new StringDeserializer(), new StringDeserializer());
            final Set<String> assigned = assignTopic(topic);
            if (assigned == null || assigned.isEmpty()) {
                throw new IllegalStateException("No partitions assigned to command consumer (topic=" + topic + ")");
            }
            // skip all old messages on the commands topic
            consumer.seekToEnd(Collections.emptyList());
            LOG.debug("Commands consumer assigned to topic {}", topic);
        } catch (final Exception e) {
            LOG.error("Failed to create consumer", e);
            if (consumer != null) {
                consumer.close();
            }
            throw e;
        }
    }

    /**
     * Assign all partitions of the given topic to given consumer.
     * This is non-incremental. I.e., the current assignment is replaced with the new one.
     *
     * @param topic The topic to assign to the given consumer;
     * @return The resulting assigned topics, i.e., actually the one given, or none at all.
     */
    private Set<String> assignTopic(final String topic) {
        LOG.debug("Assign topic {}", topic);
        final Map<String, List<PartitionInfo>> topicPartitions = consumer.listTopics();
        if (topicPartitions != null) {
            final List<PartitionInfo> partitions = topicPartitions.get(topic);
            if (partitions != null && !partitions.isEmpty()) {
                consumer.assign(partitions.stream().map(i -> new TopicPartition(i.topic(), i.partition()))
                    .collect(Collectors.toSet()));
            }
        } else {
            LOG.debug("No partitions found for topic {}", topic);
        }
        final Set<TopicPartition> assignedPartitions = consumer.assignment();
        LOG.debug("Assigned partition set {}", assignedPartitions);
        return assignedPartitions.stream().map(TopicPartition::topic).collect(Collectors.toSet());

    }

    private long getProducerIdleTime(final Properties properties) {
        if (properties.containsKey(NjamsSettings.PROPERTY_KAFKA_REPLY_PRODUCER_IDLE_TIME)) {
            try {
                return Long.parseLong(properties.getProperty(NjamsSettings.PROPERTY_KAFKA_REPLY_PRODUCER_IDLE_TIME));
            } catch (final Exception e) {
                LOG.error("Failed to parse timeout from properties", e);
            }
        }
        return DEFAULT_PRODUCER_IDLE_TIME_MS;
    }

    /**
     * This method continuously checks for new messages and makes sure there is no unused producer running.
     */
    @Override
    public void run() {
        while (!doStop) {
            ConsumerRecords<String, String> records = null;
            try {
                records = consumer.poll(Duration.ofSeconds(1));
            } catch (WakeupException e) {
                // ignore
            } catch (Exception e) {
                LOG.error("Error during polling for commands.", e);
                // trigger reconnecting
                receiver.onException(e);
                break;
            }
            if (records != null && !records.isEmpty()) {
                lastMessageProcessingTimestamp = System.currentTimeMillis();
                records.forEach(receiver::onMessage);
            } else {
                checkIdleProducer();
            }
        }
        consumer.close();
        LOG.debug("Consumer closed. Poll thread finished.");
    }

    /**
     * If no messages arrive and a producer is running longer than the given timeout, it will be closed.
     */
    private void checkIdleProducer() {
        if (receiver.hasRunningProducer()
            && System.currentTimeMillis() - lastMessageProcessingTimestamp > idleProducerTimeout) {
            receiver.closeProducer();
        }
    }

    /**
     * This method is used to stop this thread
     */
    protected synchronized void doStop() {
        doStop = true;
        consumer.wakeup();
    }
}
