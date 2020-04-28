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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ShareableReceiver;

/**
 * Overrides the common {@link JmsReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author cwinkler
 *
 */
public class SharedJmsReceiver extends JmsReceiver implements ShareableReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(SharedJmsReceiver.class);

    private final Map<Path, Njams> njamsInstances = new ConcurrentHashMap<>();

    /**
     * This method creates a String that is used as a message selector.
     *
     * @return the message selector String.
     */
    @Override
    protected String createMessageSelector() {
        if (njamsInstances.isEmpty()) {
            return null;
        }
        final String selector = njamsInstances.values().stream().map(Njams::getClientPath).map(Path::getAllPaths)
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
        synchronized (njamsInstances) {
            njamsInstances.put(njamsInstance.getClientPath(), njamsInstance);
            messageSelector = createMessageSelector();
            updateConsumer();
        }
        LOG.debug("Added client {} to shared receiver; {} attached receivers.", njamsInstance.getClientPath(),
                njamsInstances.size());
    }

    @Override
    public void removeNjams(Njams njamsInstance) {

        boolean callStop;
        synchronized (njamsInstances) {
            njamsInstances.remove(njamsInstance.getClientPath());
            callStop = njamsInstances.isEmpty();
            messageSelector = createMessageSelector();
            if (!callStop) {
                updateConsumer();
            }
        }
        LOG.debug("Removed client {} from shared receiver; {} remaining receivers.", njamsInstance.getClientPath(),
                njamsInstances.size());
        if (callStop) {
            super.stop();
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
     * This method is the MessageListener implementation. It receives JMS
     * Messages automatically.
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
            final List<Njams> instances = getNjamsTargets(msg);
            if (!instances.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Received instruction {} for {}",
                            instruction.getCommand(),
                            instances.stream().map(n -> n.getClientPath().toString()).collect(Collectors.toList()));
                }
                if (instances.size() > 1) {
                    instances.parallelStream().forEach(i -> onInstruction(instruction, i));
                } else {
                    onInstruction(instruction, instances.get(0));
                }
            } else {
                LOG.error("No client found for: {}", msg.getStringProperty("NJAMS_RECEIVER"));
                instruction.setResponseResultCode(99);
                instruction.setResponseResultMessage("Client instance not found.");
            }
            reply(msg, instruction);
        } catch (Exception e) {
            LOG.error("Error in onMessage", e);
        }
    }

    /**
     * Returns the {@link Njams} instance(s) that shall receive the given message.
     * @param msg
     * @return
     */
    private List<Njams> getNjamsTargets(Message msg) {
        try {
            final Path p = new Path(msg.getStringProperty("NJAMS_RECEIVER"));
            final Njams found = njamsInstances.get(p);
            if (found != null) {
                return Collections.singletonList(found);
            }
            final int size = p.getParts().size();
            return njamsInstances
                    .values()
                    .stream()
                    .filter(n -> size < n.getClientPath().getParts().size()
                            && p.getParts().equals(n.getClientPath().getParts().subList(0, size)))
                            .collect(Collectors.toList());
        } catch (JMSException e) {
            LOG.error("Error reading JMS message", e);
        }
        return null;
    }

    @Override
    public void onInstruction(Instruction instruction, Njams njams) {
        LOG.debug("OnInstruction: {} for {}", instruction == null ? "null" : instruction.getCommand(),
                njams.getClientPath());
        if (instruction == null) {
            LOG.error("Instruction should not be null");
            return;
        }
        if (instruction.getRequest() == null || instruction.getRequest().getCommand() == null) {
            LOG.error("Instruction should have a valid request with a command");
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("Instruction should have a valid request with a command");
            instruction.setResponse(response);
            return;
        }
        //Extend your request here. If something doesn't work as expected,
        //you can return a response that will be sent back to the server without further processing.
        Response exceptionResponse = extendRequest(instruction.getRequest());
        if (exceptionResponse != null) {
            //Set the exception response
            instruction.setResponse(exceptionResponse);
        } else {
            for (InstructionListener listener : njams.getInstructionListeners()) {
                try {
                    listener.onInstruction(instruction);
                } catch (Exception e) {
                    LOG.error("Error in InstructionListener {}", listener.getClass().getSimpleName(), e);
                }
            }
            //If response is empty, no InstructionListener found. Set default Response indicating this.
            if (instruction.getResponse() == null) {
                LOG.warn("No InstructionListener for {} found", instruction.getRequest().getCommand());
                Response response = new Response();
                response.setResultCode(1);
                response.setResultMessage(
                        "No InstructionListener for " + instruction.getRequest().getCommand() + " found");
                instruction.setResponse(response);
            }
        }
    }

}