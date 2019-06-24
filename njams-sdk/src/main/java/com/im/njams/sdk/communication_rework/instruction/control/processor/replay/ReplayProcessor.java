package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;

public class ReplayProcessor extends InstructionProcessor {

    public static final String REPLAY = Command.REPLAY.commandString();

    protected static final String WARNING_RESULT_MESSAGE = "Client cannot replay processes. No replay handler is present.";

    protected static final String ERROR_RESULT_MESSAGE_PREFIX = "Error while executing replay: ";
    private ReplayHandler replayHandler;

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
                response.setResultMessage(ERROR_RESULT_MESSAGE_PREFIX + ex.getMessage());
                instruction.setResponse(response);
                instruction.setResponseParameter(ReplayResponse.EXCEPTION_KEY, String.valueOf(ex));
            }
        } else {
            response.setResultCode(1);
            response.setResultMessage(WARNING_RESULT_MESSAGE);
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
