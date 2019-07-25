/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.instruction.control.processor.replay;

import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import com.im.njams.sdk.communication.instruction.control.processor.AbstractInstructionProcessor;

import static com.im.njams.sdk.communication.instruction.control.processor.replay.ReplayProcessor.ReplayResponseStatus.*;

/**
 * Todo: Write Doc
 */
public class ReplayProcessor extends AbstractInstructionProcessor<ReplayInstruction> {

    enum ReplayResponseStatus {
        REPLAY_HANDLER_NOT_SET("Client cannot replay processes. No replay handler is present."),
        EXCEPTION_WAS_THROWN_WHILE_REPLAYING("Error while executing replay: "),
        REPLAY_SUCCESS("");

        private String message;

        ReplayResponseStatus(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private ReplayPlugin replayPlugin;

    ReplayResponseStatus replayStatus;

    Exception caughtExceptionWhileReplaying;

    public ReplayProcessor(ReplayPlugin replayPlugin) {
        super();
        this.replayPlugin = replayPlugin;
        setBackToStart();
    }

    private void setBackToStart() {
        this.replayStatus = REPLAY_HANDLER_NOT_SET;
        this.caughtExceptionWhileReplaying = null;
    }

    @Override
    protected boolean prepareProcessing() {
        setBackToStart();
        return SUCCESS;
    }

    @Override
    protected void process() {
        if (replayPlugin.isReplayHandlerSet()) {
            tryToReplay();
        }
    }

    private void tryToReplay() {
        try {
            replay();
        } catch (final RuntimeException ex) {
            replayFailed(ex);
        }
    }

    private void replay() {
        ReplayHandler replayHandler = replayPlugin.getPluginItem();
        replayHandler.replay(getInstruction());
        replayStatus = REPLAY_SUCCESS;
    }

    private void replayFailed(RuntimeException exceptionThatWasThrown) {
        replayStatus = EXCEPTION_WAS_THROWN_WHILE_REPLAYING;
        caughtExceptionWhileReplaying = exceptionThatWasThrown;
    }

    @Override
    protected void setInstructionResponse() {
        ReplayResponseWriter writer = getInstruction().getResponseWriter();
        if (replayStatus == REPLAY_SUCCESS) {
            //Was filled by the replay() caller
        } else if (replayStatus == EXCEPTION_WAS_THROWN_WHILE_REPLAYING) {
            setExceptionWasThrownResponse(writer);
        } else { //REPLAY_HANDLER_NOT_SET
            setReplayHandlerNotSetResponse(writer);
        }
    }

    private void setExceptionWasThrownResponse(ReplayResponseWriter writer){
        final String resultMessage = replayStatus.getMessage() + caughtExceptionWhileReplaying.getMessage();
        final String errorMessage = String.valueOf(caughtExceptionWhileReplaying);
        writer.
                setResultCode(ResponseWriter.ResultCode.ERROR).
                setResultMessage(resultMessage).
                setException(errorMessage);
    }

    private void setReplayHandlerNotSetResponse(ReplayResponseWriter writer){
        writer.
                setResultCode(ResponseWriter.ResultCode.WARNING).
                setResultMessage(replayStatus.getMessage());
    }
}
