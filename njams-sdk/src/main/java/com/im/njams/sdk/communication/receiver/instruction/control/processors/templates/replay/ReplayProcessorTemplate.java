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

package com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.replay;

import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayResponseWriter;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.AbstractProcessorTemplate;

/**
 * This abstract class provides a template for processing {@link NjamsReplayInstruction replayInstructions}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class ReplayProcessorTemplate extends AbstractProcessorTemplate {

    /**
     * Replays the {@link NjamsReplayInstruction replayInstruction} and logs the response accordingly.
     */
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

    /**
     * Checks if replay can be executed.
     *
     * @return true, if replay can be started, otherwise false.
     */
    protected abstract boolean canReplay();

    /**
     * Replays the {@link NjamsReplayInstruction replayInstruction}.
     */
    protected abstract void processReplayInstruction();

    /**
     * Sets an error response to the instruction.
     *
     * @param ex the exception that has been thrown while replaying.
     */
    protected abstract void setExceptionResponse(RuntimeException ex);

    /**
     * Sets an warning response to the instruction.
     */
    protected abstract void setCantReplayResponse();

    /**
     * Returns the {@link NjamsReplayInstruction replayInstruction} to process by this
     * {@link ReplayProcessorTemplate instructionProcessor}.
     *
     * @return the instruction to process.
     */
    @Override
    public NjamsReplayInstruction getInstruction() {
        return (NjamsReplayInstruction) super.getInstruction();
    }

    /**
     * Returns the instructions {@link NjamsReplayRequestReader replayRequestReader}.
     *
     * @return the requestReader to the corresponding instruction.
     */
    @Override
    public NjamsReplayRequestReader getRequestReader() {
        return getInstruction().getRequestReader();
    }

    /**
     * Returns the instructions {@link NjamsReplayResponseWriter replayResponseWriter}.
     *
     * @return the responseWriter to the corresponding instruction.
     */
    @Override
    public NjamsReplayResponseWriter getResponseWriter() {
        return getInstruction().getResponseWriter();
    }
}
