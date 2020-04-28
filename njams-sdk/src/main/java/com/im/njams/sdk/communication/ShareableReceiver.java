/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;

/**
 * Interface to be implemented by {@link Receiver}s that support receiving messages for multiple {@link Njams} instances.
 * @author cwinkler
 *
 */
public interface ShareableReceiver extends Receiver {

    /**
     * Stops the given {@link Njams} instance from receiving messages from this receiver instance.
     * @param njams The {@link Njams} instance to be removed.
     */
    public void removeNjams(Njams njams);

    /**
     * Passes the instruction to the according {@link Njams} instance.
     * @param instruction The instruction to process.
     * @param njams The {@link Njams} instance to receive the instruction.
     * @see Receiver#onInstruction(Instruction)
     */
    public void onInstruction(Instruction instruction, Njams njams);

    /**
     * Always throws an {@link UnsupportedOperationException}. Use {@link #removeNjams(Njams)} for stopping an
     * {@link Njams} instance from receiving instructions from this receiver.
     *
     * @see com.im.njams.sdk.communication.jms.JmsReceiver#stop()
     * @throws UnsupportedOperationException always
     */
    @Override
    public default void stop() {
        throw new UnsupportedOperationException("Use removeNjams to stop an instance from receiving messages.");
    }

    /**
     * Always throws an {@link UnsupportedOperationException}. This method is replaced by
     * {@link #onInstruction(Instruction, Njams)} for passing instructions to the according {@link Njams} instance.
     *
     * @see com.im.njams.sdk.communication.AbstractReceiver#onInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction)
     * @throws UnsupportedOperationException always
     */
    @Override
    public default void onInstruction(Instruction instruction) {
        throw new UnsupportedOperationException();
    }
}
