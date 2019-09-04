/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.receiver.instruction.control.processors;


import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;

/**
 * This interface provides functionality to actually process {@link Instruction instructions} of type {@link T T}.
 * Each {@link InstructionProcessor instruction processor} is designed to listen to exactly one
 * {@link Command command}.
 *
 * @param <T> The actual type of {@link Instruction instruction} to process
 * @author krautenberg
 * @version 4.1.0
 */
public interface InstructionProcessor<T extends Instruction> {

    /**
     * Returns the {@link Command command} as String that this {@link InstructionProcessor instruction processor} was
     * designed for.
     *
     * @return the command as String to listen to
     */
    String getCommandToListenTo();

    /**
     * Processes the given {@link Instruction instruction}.
     *
     * @param instructionToProcess the instruction to process.
     */
    void processInstruction(T instructionToProcess);
}
