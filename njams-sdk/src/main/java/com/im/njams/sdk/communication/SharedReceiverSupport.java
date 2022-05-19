/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.Path;

/**
 * A helper class that implements sharing receiver instances.<br>
 * <b>Implementation notes:</b>
 * The actual implementations using this helper simply have to track adding and removing instances, and call
 * {@link #onInstruction(Object, Instruction, boolean)} for each received (and valid) instruction.
 *
 * @param <R> The actual receiver implementation
 * @param <M> The message type being processed by the receiver
 */
public class SharedReceiverSupport<R extends AbstractReceiver & ShareableReceiver<M>, M> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedReceiverSupport.class);

    private final Map<Path, Receiver> receiverInstances = new ConcurrentHashMap<>();
    private final R receiver;

    public SharedReceiverSupport(R receiver) {
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     * @param receiver The receiver to add.
     */
    public void addReceiver(Receiver receiver) {
        synchronized (receiverInstances) {
            receiverInstances.put(receiver.getInstanceMetadata().getClientPath(), receiver);
        }
        LOG.debug("Added client {} to shared receiver; {} attached receivers.",
            receiver.getInstanceMetadata().getClientPath(),
            receiverInstances.size());
    }

    /**
     * Removes an instance from this receiver.
     *
     * @param receiver The instance to remove.
     * @return <code>true</code> if the actual receiver has been stopped, i.e., when there are no more instances
     * registered with this shared receiver.
     */
    public boolean removeReceiver(Receiver receiver) {
        synchronized (receiverInstances) {
            receiverInstances.remove(receiver.getInstanceMetadata().getClientPath());
            LOG.debug("Removed client {} from shared receiver; {} remaining receivers.",
                receiver.getInstanceMetadata().getClientPath(),
                receiverInstances.size());
            if (receiverInstances.isEmpty()) {
                receiver.stop();
                return true;
            }
            return false;
        }
    }

    /**
     * Returns all currently registered {@link Receiver} instances.
     * @return All currently registered {@link Receiver} instances.
     */
    public Collection<Receiver> getAllReceiverInstances() {
        return receiverInstances.values();
    }

    /**
     * To be called for each received (and valid) instruction.
     * @param message The original message received.
     * @param instruction The instruction parsed from the original message and to be completed with a reply.
     * @param failOnMissingInstance If <code>true</code> and error is sent as reply when no matching target {@link Receiver}
     * instance was found. Otherwise, the request is simply ignored.
     */
    public void onInstruction(M message, Instruction instruction, boolean failOnMissingInstance) {
        final Path receiverPath = receiver.getReceiverPath(message, instruction);
        LOG.debug("Received instruction {} with target {}", instruction.getCommand(), receiverPath);
        if (receiverPath == null) {
            return;
        }

        final List<Receiver> instances = getReceiverTargets(receiverPath);
        if (instances != null && !instances.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Handling instruction {} with client(s) {}",
                        instruction.getCommand(),
                        instances
                            .stream()
                            .map(receiver -> receiver.getInstanceMetadata().getClientPath().toString())
                            .collect(Collectors.toList()));
            }
            if (instances.size() > 1) {
                instances.parallelStream().forEach(i -> onInstruction(instruction, i));
            } else {
                onInstruction(instruction, instances.get(0));
            }
            receiver.sendReply(message, instruction);
        } else {
            if (failOnMissingInstance) {
                LOG.error("No client found for: {}", receiverPath);
                instruction.setResponseResultCode(99);
                instruction.setResponseResultMessage("Client instance not found.");
                receiver.sendReply(message, instruction);
            } else {
                LOG.debug("No client found for: {}", receiverPath);
            }
        }
    }

    private void onInstruction(Instruction instruction, Receiver receiver) {
        if (instruction == null) {
            LOG.error("Instruction must not be null");
            return;
        }
        LOG.debug("OnInstruction: {} for {}", instruction.getCommand(), receiver.getInstanceMetadata().getClientPath());
        if (instruction.getRequest() == null || instruction.getRequest().getCommand() == null) {
            LOG.error("Instruction must have a valid request with a command");
            Response response = new Response();
            response.setResultCode(1);
            response.setResultMessage("Instruction must have a valid request with a command");
            instruction.setResponse(response);
            return;
        }
        //Extend your request here. If something doesn't work as expected,
        //you can return a response that will be sent back to the server without further processing.
        Response exceptionResponse = this.receiver.extendRequest(instruction.getRequest());
        if (exceptionResponse != null) {
            //Set the exception response
            instruction.setResponse(exceptionResponse);
        } else {
            receiver.getNjamsReceiver().distribute(instruction);
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

    private List<Receiver> getReceiverTargets(Path receiverPath) {
        try {
            final Receiver found = receiverInstances.get(receiverPath);
            if (found != null) {
                return Collections.singletonList(found);
            }
            final int size = receiverPath.getParts().size();
            return receiverInstances
                    .values()
                    .stream()
                    .filter(receiver -> size < receiver.getInstanceMetadata().getClientPath().getParts().size()
                            && receiverPath.getParts().equals(
                        receiver.getInstanceMetadata().getClientPath().getParts().subList(0, size)))
                            .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Failed to resolve instruction receiver.", e);
        }
        return null;
    }
}
