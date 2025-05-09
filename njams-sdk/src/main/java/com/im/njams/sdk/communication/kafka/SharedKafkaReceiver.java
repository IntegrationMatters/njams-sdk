/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import com.im.njams.sdk.communication.fragments.RawMessage;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Overrides the common {@link KafkaReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author cwinkler
 */
public class SharedKafkaReceiver extends KafkaReceiver implements ShareableReceiver<ConsumerRecord<?, ?>> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedKafkaReceiver.class);

    private final SharedReceiverSupport<SharedKafkaReceiver, ConsumerRecord<?, ?>> sharingSupport =
        new SharedReceiverSupport<>(this);

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     * @see com.im.njams.sdk.communication.jms.JmsReceiver#setNjams(com.im.njams.sdk.Njams)
     */
    @Override
    public void setNjams(final Njams njamsInstance) {
        super.setNjams(null);
        sharingSupport.addNjams(njamsInstance);
    }

    @Override
    public void removeNjams(final Njams njamsInstance) {
        sharingSupport.removeNjams(njamsInstance);
    }

    @Override
    public Path getReceiverPath(final ConsumerRecord<?, ?> requestMessage, final Instruction instruction) {
        return new Path(getHeader(requestMessage, NJAMS_RECEIVER_HEADER));
    }

    @Override
    public String getClientId(ConsumerRecord<?, ?> requestMessage, Instruction instruction) {
        return getHeader(requestMessage, NJAMS_CLIENTID_HEADER);
    }

    @Override
    public void sendReply(final ConsumerRecord<?, ?> requestMessage, final Instruction reply, String clientId) {
        sendReply(getHeader(requestMessage, NJAMS_MESSAGE_ID_HEADER), reply, clientId);

    }

    @Override
    public synchronized void start() {
        if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            super.start();
        }
    }

    /**
     * This method is the MessageListener implementation. It receives JMS
     * Messages automatically.
     *
     * @param msg the newly arrived JMS message.
     */
    @Override
    public void onMessage(final ConsumerRecord<String, String> msg) {
        try {

            if (!isValidMessage(msg)) {
                return;
            }
            final RawMessage raw = chunkAssembly.resolve(msg);
            if (raw == null) {
                LOG.debug("Received partial message");
                return;
            }

            final Instruction instruction = parseInstruction(raw);
            if (instruction == null) {
                return;
            }
            sharingSupport.onInstruction(msg, instruction, false);
        } catch (final Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    @Override
    protected boolean isValidMessage(final ConsumerRecord<?, ?> msg) {
        if (msg == null || StringUtils.isNotBlank(getHeader(msg, NJAMS_REPLY_FOR_HEADER))) {
            // skip messages sent as a reply
            return false;
        }
        if (StringUtils.isBlank(getHeader(msg, NJAMS_MESSAGE_ID_HEADER))) {
            LOG.error("Missing request ID in message: {}", msg);
            return false;
        }
        if (StringUtils.isBlank(getHeader(msg, NJAMS_RECEIVER_HEADER))) {
            LOG.error("Missing receiver in message: {}", msg);
            return false;
        }
        if (!CONTENT_TYPE_JSON.equalsIgnoreCase(getHeader(msg, NJAMS_CONTENT_HEADER))) {
            LOG.debug("Received non json instruction -> ignore");
            return false;
        }

        return true;
    }

}