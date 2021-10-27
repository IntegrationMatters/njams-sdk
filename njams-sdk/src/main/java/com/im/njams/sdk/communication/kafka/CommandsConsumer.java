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

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread for a KafkaConsumer, that constantly needs to check if there are new commands in its Kafka topic
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class CommandsConsumer extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(CommandsConsumer.class);

    private KafkaConsumer<String, String> consumer;
    private long lastMessageProcessingTimestamp;
    private final long idleProducerTimeout;
    private final KafkaReceiver receiver;
    private boolean doStop = false;

    /**
     * This constructor saves the given properties and creates a KafkaConsumer
     *
     * @param properties the Properties that are used for connecting
     * @param topic      topic s to subscribe to
     * @param receiver   the KafkaReceiver from where this method has been called
     */
    protected CommandsConsumer(Properties properties, String topic, KafkaReceiver receiver) {
        super("kafka_commands_consumer");
        idleProducerTimeout = getTimeout(properties);
        this.receiver = receiver;

        try {
            consumer =
                    new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
            consumer.subscribe(Collections.singleton(topic));
            LOG.debug("Commands consumer subscribed on {}", topic);
        } catch (Exception e) {
            LOG.error("Failed to create consumer", e);
            if (consumer != null) {
                consumer.close();
            }
            throw e;
        }
    }

    private long getTimeout(Properties properties) {
        if (properties.containsKey(KafkaConstants.REPLY_PRODUCER_IDLE_TIME)) {
            try {
                return Long.valueOf(properties.getProperty(KafkaConstants.REPLY_PRODUCER_IDLE_TIME));
            } catch (Exception e) {
                LOG.error("Faileds to parse timeout from properties", e);
            }
        }
        return 30000;
    }

    /**
     * The main Execution of this Thread, is called by super.start(). This method
     * continuously checks for new messages and makes sure there is no unused
     * producer running.
     */
    @Override
    public void run() {
        while (!doStop) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            if (!records.isEmpty()) {
                lastMessageProcessingTimestamp = System.currentTimeMillis();
                for (ConsumerRecord<String, String> record : records) {
                    receiver.onMessage(record);
                }
            } else {
                checkIdleProducer();
            }
        }
        consumer.close();
    }

    /**
     * If no messages
     * arrive and a producer is running longer than the given timeout, it will be
     * closed.
     *
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
    }
}
