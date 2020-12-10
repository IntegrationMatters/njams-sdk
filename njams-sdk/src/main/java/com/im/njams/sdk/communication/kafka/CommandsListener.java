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
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Thread for a KafkaConsumer, that constantly needs to check if there are new
 * Commands in its Kafka topic
 *
 * @author sfaiz
 * @version 4.2.0-SNAPSHOT
 */
public class CommandsListener extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(CommandsListener.class);

	private ConsumerRecords<String, String> records;
	private KafkaConsumer<String, String> consumer;
	private long lastMessageProcessingTimestamp;
	private long idleProducerTimeout = 30000;
	private KafkaReceiver receiver;
	Boolean doStop = false;

	/**
	 * This constructor saves the given properties and creates a KafkaConsumer
	 *
	 * @param properties the Properties that are used for connecting
	 * @param topics     topics to subscribe to
	 * @param receiver   the KafkaReceiver from where this method has been called
	 */
	protected CommandsListener(Properties properties, List<String> topics, KafkaReceiver receiver) {
		if (properties.contains(KafkaConstants.IDLE_COMMANDS_RESPONSE_PRODUCER_TIMEOUT))
			idleProducerTimeout = (long) properties.get(KafkaConstants.IDLE_COMMANDS_RESPONSE_PRODUCER_TIMEOUT);
		this.receiver = receiver;

		try {
			consumer = new KafkaConsumer<String, String>(properties);
			consumer.subscribe(topics);
		} catch (Exception e) {
			consumer.close();
			LOG.info("Consumer failed", e);
		}
	}

	/**
	 * The main Execution of this Thread, is called by super.start(). This method
	 * continuously checks for new messages and makes sure there is no unused
	 * producer running.
	 */
	@Override
	public void run() {
		while (doStop == false) {
			records = consumer.poll(Duration.ofMillis(1000));

			checkIdleProducer(idleProducerTimeout);

			for (ConsumerRecord<String, String> record : records) {
				receiver.onMessage(record);
			}
		}
	}

	/**
	 * This method sets a timestamp if messages have been received. if no messages
	 * arrive, and a producer is running longer than the givven timeout, he will be
	 * closed.
	 *
	 * @param timeout, detemines how long to wait before closing an unused producer
	 */
	private void checkIdleProducer(long timeout) {
		if (!records.isEmpty())
			lastMessageProcessingTimestamp = System.currentTimeMillis();
		else if (receiver.hasRunningProducer() && System.currentTimeMillis() - lastMessageProcessingTimestamp > timeout)
			receiver.closeProducer();
	}

	/**
	 * This method is used to stop this Thred
	 */
	protected synchronized void doStop() {
		this.doStop = true;
	}
}
