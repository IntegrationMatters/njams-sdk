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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Kafka implementation for a Receiver.
 *
 * @author sfaiz
 * @version 4.1.5
 */
public class KafkaReceiver extends AbstractReceiver {

	private final Logger LOG = LoggerFactory.getLogger(KafkaReceiver.class);
	
	private KafkaProducer<String, String> producer;
	private CommandsListener listener;
	private Properties properties;
	private ObjectMapper mapper;
	private String topicName;

    /**
     * Initializes this Receiver via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMUNICATION_NAME}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#DESTINATION}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMANDS_DESTINATION}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
	@Override
	public void init(Properties props) {
		connectionStatus = ConnectionStatus.DISCONNECTED;
		mapper = JsonSerializerFactory.getDefaultMapper();
		properties = props;
		if (props.containsKey(KafkaConstants.COMMANDS_DESTINATION)) {
			topicName = props.getProperty(KafkaConstants.COMMANDS_DESTINATION);
		} else {
			topicName = props.getProperty(KafkaConstants.DESTINATION) + ".commands";
		}

		String path = props.getProperty("clientPath").replace(">", ".");
		topicName = topicName + path.substring(0, path.length()-1);
	}

	/**
	 * This method is called when the receiver has to connect. It can't be started
	 * if init(..) hasn't been called beforehand.
	 */
	@Override
	public synchronized void connect() {
		if (!isConnected()) {
			connectionStatus = ConnectionStatus.CONNECTING;

			tryToConnect(properties);

			connectionStatus = ConnectionStatus.CONNECTED;
		}
	}

	/**
	 * This method tries to create a CommandsListener, which is a separate Thread for a Consumer, constantly polling.
	 * It throws an NjamsSdkRuntimeException if any of the resources throws any exception.
	 *
	 * @param props the Properties that are used for connecting.
	 */
	private void tryToConnect(Properties props) {
		try {
			listener = new CommandsListener(properties, Collections.singletonList(topicName), this);
			listener.start();
		} catch (Exception e) {
			this.closeAll();
			throw new NjamsSdkRuntimeException("Unable to start the Commands-Consumer-Thread", e);
		}
	}
	
	/**
	 * This method sets the connectionStatus to DISCONNECTED and closes all resources
	 */
	private synchronized void closeAll() {
		if (!isConnected()) {
			return;
		}
		connectionStatus = ConnectionStatus.DISCONNECTED;
		listener.doStop();
		producer.close();
	}

	/**
	 * This method stops the Kafka Receiver, if its status is CONNECTED.
	 */
	@Override
	public void stop() {
		if (!isConnected()) {
			return;
		}
		this.closeAll();
		LOG.info("{} has been stopped successfully.", getClass().getSimpleName());
	}

	/**
	 * This method is called by the CommandsListener if a Message arrives. 
	 *
	 * @param msg the newly arrived Kafka message.
	 */
	public void onMessage(ConsumerRecord<String, String> msg) {
		try { 
			final String njamsContent = new String(msg.headers().lastHeader("NJAMS_CONTENT").value());
			if (!njamsContent.equalsIgnoreCase("json")) {
				LOG.debug("Received non json instruction -> ignore");
				return;
			}
			final Instruction instruction = getInstruction(msg);
			if (instruction == null) {
				return;
			}
			
			onInstruction(instruction);
			reply(msg, instruction);
		} catch (Exception e) {
			LOG.error("Error in onMessage", e);
		}
	}

    /**
     * This method tries to extract the Instruction out of the provided message.
     * It maps the Json string to an Instruction object.
     *
     * @param message the Json Message
     * @return the Instruction object that was extracted or null, if no valid
     * instruction was found or it could be parsed to an instruction object.
     */
	protected Instruction getInstruction(ConsumerRecord<String, String> message) {
		try {
			Instruction instruction = mapper.readValue(message.value().toString(), Instruction.class);
			if (instruction.getRequest() != null) {
				return instruction;
			}
		} catch (Exception e) {
			LOG.error("Error deserializing Instruction", e);
		}
		LOG.warn("MSG is not a valid Instruction");
		return null;
	}

	/**
	 * This method tries to reply the instructions response back to the sender. Send
	 * a message to the Sender that is mentioned in the message. If a UUID is set 
	 * in the message, it will be forwarded as well.
	 *
	 * @param message     the destination where the response will be sent to and the
	 *                    UUID are saved in here.
	 * @param instruction the instruction that holds the response.
	 */
	protected void reply(ConsumerRecord<String, String> message, Instruction instruction) {
		String responseTopic = topicName + ".response";
		if (producer == null)
			producer = new KafkaProducer<String, String>(properties);
		try {
			String response = mapper.writeValueAsString(instruction);

			List<Header> headers = new LinkedList<Header>();
			String uuid = message.headers().headers("UUID").iterator().next().toString();
            if (uuid != null && !uuid.isEmpty()) {
    			headers.add(new RecordHeader("UUID", uuid.getBytes()));
            }
			producer.send(new ProducerRecord<String, String>(responseTopic ,0 , "", response, headers));
			
			LOG.debug("Response: {}", response);
		} catch (Exception e) {
			LOG.error("Error while sending reply for {}", topicName, e);
		}
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
	protected boolean hasRunningProducer() {
		return(producer != null);
	}

	/**
	 * Closes the Producer and sets it to null
	 */
	protected void closeProducer() {
		producer.close();
		producer = null;
	}
}
