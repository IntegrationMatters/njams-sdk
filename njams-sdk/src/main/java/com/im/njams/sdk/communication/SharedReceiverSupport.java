package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A helper class that implements sharing receiver instances.<br>
 * <b>Implementation notes:</b>
 * The actual implementations using this helper simply have to track adding and removing instances, and call
 * {@link #onInstruction(Object, Instruction, boolean)} for each received (and valid) instruction.
 *
 * @param <R> The actual receiver implementation
 * @param <M> The message type being processed by the receiver
 * @author cwinkler
 * @since 4.2.0
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
     * @param njamsInstance The instance to add.
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
     * @param njamsInstance The instance to remove.
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
     *
     * @return All currently registered {@link Njams} instances.
     */
    public Collection<Njams> getAllNjamsInstances() {
        return njamsInstances.values();
    }

    /**
     * To be called for each received (and valid) instruction.
     *
     * @param message               The original message received.
     * @param instruction           The instruction parsed from the original message and to be completed with a reply.
     * @param failOnMissingInstance If <code>true</code> and error is sent as reply when no matching target {@link Njams}
     *                              instance was found. Otherwise, the request is simply ignored.
     */
    public void onInstruction(M message, Instruction instruction, boolean failOnMissingInstance) {
        final Path receiverPath = receiver.getReceiverPath(message, instruction);
        final String clientId = receiver.getClientId(message, instruction);
        LOG.debug("Received instruction {} with target {}", instruction.getCommand(), receiverPath);
        if (clientId != null) {
            LOG.debug("ClientId in instruction is {}", clientId);
        }
        if (receiverPath == null) {
            return;
        }

        final Njams njamsTarget = getNjamsTarget(receiverPath, clientId);
        if (njamsTarget != null) {
            LOG.debug("TargetReceiver found for instruction.");
            onInstruction(instruction, njamsTarget);
            if (!CommonUtils.ignoreReplayResponseOnInstruction(instruction)) {
                receiver.sendReply(message, instruction, clientId);
            }
        } else {
            if (failOnMissingInstance) {
                LOG.error("No client found for: {}", receiverPath);
                instruction.setResponseResultCode(99);
                instruction.setResponseResultMessage("Client instance not found.");
                receiver.sendReply(message, instruction, clientId);
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

    private Njams getNjamsTarget(Path receiverPath, String clientId) {
        try {
            Njams target = null;
            target = findNjamsByClientId(clientId);
            if (target != null) return target;

            target = njamsInstances.get(receiverPath);
            if (target != null) return target;

        } catch (Exception e) {
            LOG.error("Error during resolve of instruction receiver.", e);
        }

        LOG.error("Failed to resolve instruction receiver.");
        return null;
    }

    private Njams findNjamsByClientId(String clientId) {
        if (clientId != null) {
            for (Map.Entry<Path, Njams> entry : njamsInstances.entrySet()) {
                if (clientId.equals(entry.getValue().getClientId())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

}
