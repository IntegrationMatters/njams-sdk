/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;

/**
 * Todo: Write Doc
 */
public class ReplayProcessor extends InstructionProcessor {

    /**
     * Todo: Write Doc
     */
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
