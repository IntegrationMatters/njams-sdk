package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;

public class Replay extends InstructionProcessor {

    private ReplayHandler replayHandler;

    public static final String REPLAY = Command.REPLAY.commandString();

    public Replay(ReplayHandler replayHandler, String commandToProcess) {
        super(commandToProcess);
        this.replayHandler = replayHandler;
    }

    @Override
    public void processInstruction(Instruction instruction) {
        if (instruction.getRequest().getCommand().equalsIgnoreCase(Command.REPLAY.commandString())) {
            final Response response = new Response();
            if (replayHandler != null) {
                try {
                    ReplayResponse replayResponse = replayHandler.replay(new ReplayRequest(instruction));
                    replayResponse.addParametersToInstruction(instruction);
                } catch (final Exception ex) {
                    response.setResultCode(2);
                    response.setResultMessage("Error while executing replay: " + ex.getMessage());
                    instruction.setResponse(response);
                    instruction.setResponseParameter("Exception", String.valueOf(ex));
                }
            } else {
                response.setResultCode(1);
                response.setResultMessage("Client cannot replay processes. No replay handler is present.");
                instruction.setResponse(response);
            }
        }
    }
}
