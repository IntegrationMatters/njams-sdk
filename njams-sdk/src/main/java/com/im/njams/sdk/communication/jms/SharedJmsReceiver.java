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
package com.im.njams.sdk.communication.jms;

import static com.im.njams.sdk.communication.MessageHeaders.CONTENT_TYPE_JSON;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CLIENTID_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CONTENT_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HEADER;

import java.util.Collection;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Overrides the common {@link JmsReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author cwinkler
 */
public class SharedJmsReceiver extends JmsReceiver implements ShareableReceiver<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedJmsReceiver.class);

    private final SharedReceiverSupport<SharedJmsReceiver, Message> sharingSupport =
        new SharedReceiverSupport<>(this);

    /**
     * Has to provide all {@link Njams} instances that use this receiver for selecting the commands addressed to any
     * of these instances.
     * @return All instances that use this receiver.
     */
    @Override
    protected Collection<Njams> provideNjamsInstances() {
        return sharingSupport.getAllNjamsInstances();
    }

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     * @see com.im.njams.sdk.communication.jms.JmsReceiver#setNjams(com.im.njams.sdk.Njams)
     */
    @Override
    public void setNjams(Njams njamsInstance) {
        super.setNjams(null);
        sharingSupport.addNjams(njamsInstance);
        synchronized (this) {
            updateFilters();
            if (useMessageselector) {
                updateConsumer();
            }
        }
    }

    @Override
    public void removeNjams(Njams njamsInstance) {
        if (sharingSupport.removeNjams(njamsInstance)) {
            synchronized (this) {
                updateFilters();
                if (useMessageselector) {
                    updateConsumer();
                }
            }
        }
    }

    private void updateConsumer() {
        if (!isConnected()) {
            return;
        }

        try {
            if (consumer != null) {
                consumer.close();
            }
            consumer = createConsumer(session, topic);
        } catch (Exception e) {
            LOG.error("Failed to update consumer", e);
        }
    }

    /**
     * This method is the MessageListener implementation. It receives JMS messages automatically.
     *
     * @param msg the newly arrived JMS message.
     */
    @Override
    public void onMessage(Message msg) {
        if (!messageFilter.test(msg)) {
            return;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {}", StringUtils.messageToString(msg));
        }

        try {
            final String njamsContent = msg.getStringProperty(NJAMS_CONTENT_HEADER);
            if (!CONTENT_TYPE_JSON.equalsIgnoreCase(njamsContent)) {
                LOG.debug("Received non json instruction -> ignore");
                return;
            }
            final Instruction instruction = getInstruction(msg);
            if (instruction == null) {
                return;
            }
            sharingSupport.onInstruction(msg, instruction, true);
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    @Override
    public Path getReceiverPath(Message requestMessage, Instruction instruction) {
        try {
            return new Path(requestMessage.getStringProperty(NJAMS_RECEIVER_HEADER));
        } catch (JMSException e) {
            LOG.error("Error reading JMS property", e);
        }
        return null;
    }

    @Override
    public String getClientId(Message requestMessage, Instruction instruction) {
        try {
            return requestMessage.getStringProperty(NJAMS_CLIENTID_HEADER);
        } catch (JMSException e) {
            LOG.error("Error reading JMS property", e);
        }
        return null;
    }

    @Override
    public void sendReply(Message requestMessage, Instruction reply, String clientId) {
        reply(requestMessage, reply, clientId);
    }

}