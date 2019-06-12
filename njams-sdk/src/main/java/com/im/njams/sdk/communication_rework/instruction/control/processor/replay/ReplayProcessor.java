package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;

public class ReplayProcessor extends InstructionProcessor {

    private ReplayHandler replayHandler;

    public static final String REPLAY = Command.REPLAY.commandString();

    public ReplayProcessor() {
        super(REPLAY);
    }

    @Override
    public void processInstruction(Instruction instruction) {
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

    public void setReplayHandler(ReplayHandler replayHandler){
        this.replayHandler = replayHandler;
    }

    public ReplayHandler getReplayHandler(){
        return replayHandler;
    }
}
