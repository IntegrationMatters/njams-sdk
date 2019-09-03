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

package com.im.njams.sdk.communication.receiver.instruction.control.templates;

import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayResponseWriter;

public abstract class ReplayProcessorTemplate extends InstructionProcessorTemplate<NjamsReplayInstruction> {

    @Override
    protected void process() {
        if (canReplay()) {
            try {
                processReplayInstruction();
            } catch (final RuntimeException ex) {
                setExceptionResponse(ex);
            }
        } else {
            setCantReplayResponse();
        }
    }

    protected abstract boolean canReplay();

    protected abstract void processReplayInstruction();

    protected abstract void setExceptionResponse(RuntimeException ex);

    protected abstract void setCantReplayResponse();

    public NjamsReplayRequestReader getReplayRequestReader() {
        return getInstruction().getRequestReader();
    }

    public NjamsReplayResponseWriter getReplayResponseWriter() {
        return getInstruction().getResponseWriter();
    }
}
