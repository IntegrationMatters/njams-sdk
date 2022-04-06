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
import com.im.njams.sdk.common.Path;

/**
 * Interface to be implemented by {@link Receiver}s that support receiving messages for multiple {@link Receiver} instances.
 * @author cwinkler
 *
 * @param <M> The raw message type that is received from the transport API
 *
 */
public interface ShareableReceiver<M> extends Receiver {

    /**
     * Stops the given {@link Receiver} instance from receiving messages from this receiver instance.
     * @param clientPath The key for the {@link Receiver} instance to be removed.
     */
    void removeReceiver(Path clientPath);

    void addReceiver(Path clientPath, Receiver receiver);

    /**
     * Has to extract the receiver instance (client) path, i.e., the path that matches a certain
     *
     * @param requestMessage The raw message read from the transport API
     * @param instruction The instruction parsed from the received message
     * @return {@link Path} of the receiver client instance.
     */
    Path getReceiverPath(M requestMessage, Instruction instruction);

    /**
     * Sends the given reply message as response to the given request message.
     * @param requestMessage The raw message read from the transport API
     * @param reply The instruction parsed from the received message
     */
    void sendReply(M requestMessage, Instruction reply);

    /**
     * Always throws an {@link UnsupportedOperationException}. This method is replaced by
     *
     * @see com.im.njams.sdk.communication.AbstractReceiver#onInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction)
     * @throws UnsupportedOperationException always
     */
    @Override
    default void onInstruction(Instruction instruction) {
        throw new UnsupportedOperationException();
    }
}
