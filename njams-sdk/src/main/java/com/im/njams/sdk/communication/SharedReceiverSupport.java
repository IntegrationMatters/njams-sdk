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
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;

/**
 * A helper class that implements sharing receiver instances.<br>
 * <b>Implementation notes:</b>
 * The actual implementations using this helper simply have to track adding and removing instances, and call
 * {@link #onInstruction(Object, Instruction, boolean)} for each received (and valid) instruction.
 *
 * @author cwinkler
 * @since 4.2.0
 *
 * @param <R> The actual receiver implementation
 * @param <M> The message type being processed by the receiver
 */
public class SharedReceiverSupport<R extends AbstractReceiver & ShareableReceiver<M>, M> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedReceiverSupport.class);

    private final Map<Path, Njams> njamsInstances = new ConcurrentHashMap<>();
    private final R receiver;

    public SharedReceiverSupport(R receiver) {
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     * @see com.im.njams.sdk.communication.jms.JmsReceiver#setNjams(com.im.njams.sdk.Njams)
     */
    public void addNjams(Njams njamsInstance) {
        synchronized (njamsInstances) {
            njamsInstances.put(njamsInstance.getClientPath(), njamsInstance);
        }
        LOG.debug("Added client {} to shared receiver; {} attached receivers.", njamsInstance.getClientPath(),
                njamsInstances.size());
    }

    /**
     * Removes an instance from this receiver.
     *
     * @param njamsInstance
     * @return <code>true</code> if the actual receiver has been stopped, i.e., when there are no more instances
     * registered with this shared receiver.
     */
    public boolean removeNjams(Njams njamsInstance) {

        synchronized (njamsInstances) {
            njamsInstances.remove(njamsInstance.getClientPath());
            LOG.debug("Removed client {} from shared receiver; {} remaining receivers.", njamsInstance.getClientPath(),
                    njamsInstances.size());
            if (njamsInstances.isEmpty()) {
                receiver.stop();
                return true;
            }
            return false;
        }
    }

    /**
     * Returns all currently registered {@link Njams} instances.
     * @return
     */
    public Collection<Njams> getAllNjamsInstances() {
        return njamsInstances.values();
    }

    /**
     * To be called for each received (and valid) instruction.
     * @param message The original message received.
     * @param instruction The instruction parsed from the original message and to be completed with a reply.
     * @param failOnMissingInstance If <code>true</code> and error is sent as reply when no matching target {@link Njams}
     * instance was found. Otherwise, the request is simply ignored.
     */
    public void onInstruction(M message, Instruction instruction, boolean failOnMissingInstance) {
        final Path receiverPath = receiver.getReceiverPath(message);
        LOG.debug("Received instruction {} with target {}", instruction.getCommand(), receiverPath);
        if (receiverPath == null) {
            return;
        }

        final List<Njams> instances = getNjamsTargets(receiverPath);
        if (!instances.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Handling instruction {} with client(s) {}",
                        instruction.getCommand(),
                        instances.stream().map(n -> n.getClientPath().toString()).collect(Collectors.toList()));
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

    private void onInstruction(Instruction instruction, Njams njams) {
        if (instruction == null) {
            LOG.error("Instruction must not be null");
            return;
        }
        LOG.debug("OnInstruction: {} for {}", instruction.getCommand(), njams.getClientPath());
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
        Response exceptionResponse = receiver.extendRequest(instruction.getRequest());
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

    private List<Njams> getNjamsTargets(Path receiverPath) {
        try {
            final Njams found = njamsInstances.get(receiverPath);
            if (found != null) {
                return Collections.singletonList(found);
            }
            final int size = receiverPath.getParts().size();
            return njamsInstances
                    .values()
                    .stream()
                    .filter(n -> size < n.getClientPath().getParts().size()
                            && receiverPath.getParts().equals(n.getClientPath().getParts().subList(0, size)))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Failed to resolve instruction receiver.", e);
        }
        return null;
    }

}
