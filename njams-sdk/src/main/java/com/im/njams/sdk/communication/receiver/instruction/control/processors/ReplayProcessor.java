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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.ReplayProcessorTemplate;

/**
 * Todo: Write Doc
 */
public class ReplayProcessor extends ReplayProcessorTemplate {

    static final String REPLAY_PLUGIN_NOT_SET = "Client cannot replay processes. No replay handler is present.";

    static final String EXCEPTION_WAS_THROWN_WHILE_REPLAYING = "Error while executing replay: ";

    private ReplayPlugin replayPlugin;

    public ReplayProcessor(ReplayPlugin replayPlugin) {
        super();
        this.replayPlugin = replayPlugin;
    }

    @Override
    protected boolean canReplay() {
        return replayPlugin.isReplayHandlerSet();
    }

    @Override
    protected void processReplayInstruction() {
        ReplayHandler replayHandler = replayPlugin.getPluginItem();
        replayHandler.replay(getInstruction());
    }

    @Override
    protected void setExceptionResponse(RuntimeException ex) {
        final String resultMessage = EXCEPTION_WAS_THROWN_WHILE_REPLAYING + ex.getMessage();
        final String errorMessage = String.valueOf(ex);
        getReplayResponseWriter().
                setResultCodeAndResultMessage(ResultCode.ERROR, resultMessage).
                setException(errorMessage);
    }

    @Override
    protected void setCantReplayResponse() {
        getReplayResponseWriter().
                setResultCodeAndResultMessage(ResultCode.WARNING, REPLAY_PLUGIN_NOT_SET);
    }
}
