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

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
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
import com.im.njams.sdk.utils.JsonUtils;

/**
 * Kafka implementation for a Sender.
 *
 * @author sfaiz
 * @version 4.1.5
 */
public class KafkaSender extends AbstractSender {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaSender.class);

    private KafkaProducer<String, String> producer;
    private String destination;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMUNICATION_NAME}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#DESTINATION}
     * <li>{@value com.im.njams.sdk.communication.kafka.KafkaConstants#COMMANDS_DESTINATION}
     * </ul>
     * See all valid properties in KafkaConstants
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        super.init(properties);
        if (properties.containsKey("destination"))
            destination = properties.getProperty("destination") + ".event";
        else {
            LOG.info("No destination provided. Using default: njams");
            destination = "njams.event";
        }
        try {
            connect();
            LOG.debug("Initialized sender {}", KafkaConstants.COMMUNICATION_NAME);
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize sender {}\n", KafkaConstants.COMMUNICATION_NAME, e);
        }
    }

    /**
     * Create the KafkaProducer for Events.
     */
    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            producer = new KafkaProducer<String, String>(properties);
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            producer.close();

            throw new NjamsSdkRuntimeException("Unable to connect", e);
        }
    }

    /**
     * Send the given LogMessage to the specified Kafka.
     *
     * @param msg the Logmessage to send
     */
    @Override
    protected void send(LogMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_EVENT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send LogMessage {} to {}:\n{}", msg.getPath(), destination, data);
            } else {
                LOG.debug("Send Logmessage for {} to {}", msg.getPath(), destination);
            }
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send LogMessage", e);
        }
    }

    /**
     * Send the given ProjectMessage to the specified Kafka.
     *
     * @param msg the Projectmessage to send
     */
    @Override
    protected void send(ProjectMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_PROJECT, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send ProjectMessage {} to {}:\n{}", msg.getPath(), destination, data);
            } else {
                LOG.debug("Send ProjectMessage for {} to {}", msg.getPath(), destination);
            }
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to send ProjectMessage", e);
        }
    }

    /**
     * Send the given Tracemessage to the specified Kafka.
     *
     * @param msg the Tracemessage to send
     */
    @Override
    protected void send(TraceMessage msg) throws NjamsSdkRuntimeException {
        try {
            String data = JsonUtils.serialize(msg);
            sendMessage(msg, Sender.NJAMS_MESSAGETYPE_TRACE, data);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Send TraceMessage {} to {}:\n{}", msg.getPath(), destination, data);
            } else {
                LOG.debug("Send TraceMessage for {} to {}", msg.getPath(), destination);
            }
        } catch (Exception e) {
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
    protected void sendMessage(CommonMessage msg, String messageType, String data) throws InterruptedException {
        List<Header> headers = new LinkedList<Header>();
        if (msg instanceof LogMessage)
            headers.add(new RecordHeader(Sender.NJAMS_LOGID, ((LogMessage) msg).getLogId().getBytes()));
        headers.add(new RecordHeader(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString().getBytes()));
        headers.add(new RecordHeader(Sender.NJAMS_MESSAGETYPE, messageType.getBytes()));
        headers.add(new RecordHeader(Sender.NJAMS_PATH, msg.getPath().getBytes()));

        tryToSend(new ProducerRecord<String, String>(destination, 0, "", data, headers));
    }

    /**
     * Try to send and catches Errors
     *
     * @param message
     * @throws InterruptedException
     */
    private void tryToSend(ProducerRecord<String, String> message) throws InterruptedException {
        boolean sended = false;
        final int EXCEPTION_IDLE_TIME = 50;
        final int MAX_TRIES = 100;
        int tries = 0;

        do {
            try {
                producer.send(message);
                sended = true;
            } catch (KafkaException | IllegalStateException e) {
                if (discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS) {
                    LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                    DiscardMonitor.discard();
                    break;
                }
                if (++tries >= MAX_TRIES) {
                    LOG.warn("Try to reconnect, because the Topic couldn't be reached after {} seconds.",
                            MAX_TRIES * EXCEPTION_IDLE_TIME);
                    throw e;
                } else {
                    Thread.sleep(EXCEPTION_IDLE_TIME);
                }
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
            } catch (KafkaException ex) {
                LOG.warn("Unable to close connection", ex);
            } catch (IllegalArgumentException ex) {
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
