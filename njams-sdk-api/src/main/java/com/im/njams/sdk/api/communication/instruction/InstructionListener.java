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

package com.im.njams.sdk.api.communication.instruction;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;

/**
 * This interface provides functionality to process an {@link Instruction instruction} that was sent by the server.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public interface InstructionListener extends AutoCloseable {

    /**
     * The {@link Instruction.RequestReader request} will be read, processed and a {@link Instruction.ResponseWriter}
     * response} will be created and set to the {@link Instruction instruction}.
     *
     * @param instruction the instruction with the given {@link Instruction.RequestReader request} and
     *                    {@link Instruction.ResponseWriter response}.
     */
    void onInstruction(Instruction instruction);
}
