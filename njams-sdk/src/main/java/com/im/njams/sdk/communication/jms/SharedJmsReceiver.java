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

import java.util.Collection;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;

/**
 * Overrides the common {@link JmsReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author cwinkler
 *
 */
public class SharedJmsReceiver extends JmsReceiver implements ShareableReceiver<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedJmsReceiver.class);

    private final SharedReceiverSupport<SharedJmsReceiver, Message> sharingSupport =
            new SharedReceiverSupport<>(this);

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    @Override
    protected String createMessageSelector() {
        final Collection<Njams> njamsInstances = sharingSupport.getAllNjamsInstances();
        if (njamsInstances.isEmpty()) {
            return null;
        }
        final String selector = njamsInstances.stream().map(Njams::getClientPath).map(Path::getAllPaths)
                .flatMap(Collection::stream).collect(Collectors.toSet()).stream().map(Object::toString).sorted()
                .collect(Collectors.joining("' OR NJAMS_RECEIVER = '", "NJAMS_RECEIVER = '", "'"));
        LOG.debug("Updated message selector: {}", selector);
        return selector;
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
            messageSelector = createMessageSelector();
            updateConsumer();
        }
    }

    @Override
    public void removeNjams(Njams njamsInstance) {
        if (sharingSupport.removeNjams(njamsInstance)) {
            synchronized (this) {
                messageSelector = createMessageSelector();
                updateConsumer();
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
        try {
            final String njamsContent = msg.getStringProperty("NJAMS_CONTENT");
            if (!njamsContent.equalsIgnoreCase("json")) {
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
    public Path getReceiverPath(Message requestMessage) {
        try {
            return new Path(requestMessage.getStringProperty("NJAMS_RECEIVER"));
        } catch (JMSException e) {
            LOG.error("Error reading JMS property", e);
        }
        return null;
    }

    @Override
    public void sendReply(Message requestMessage, Instruction reply) {
        reply(requestMessage, reply);
    }

}