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
package com.im.njams.sdk.communication.receiver.instruction.control;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import com.im.njams.sdk.communication.receiver.instruction.entity.InstructionProcessorCollection;

/**
 * This class dispatches the {@link Instruction instructions} that need to be processed to the corresponding
 * {@link InstructionProcessor instructionProcessors}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class InstructionController {

    private InstructionProcessorCollection instructionProcessorCollection = new InstructionProcessorCollection();

    /**
     * Puts an {@link InstructionProcessor instructionProcessor} for the given {@link String commandToListenTo} to
     * the processing {@link InstructionProcessor instructionProcessors}. If a InstructionProcessor with the same
     * {@link String commandToListenTo} is found, the old InstructionProcessor will be overwritten by the new
     * instructionProcessor.
     *
     * @param commandToListenTo    the command the instructionProcessor is listening to
     * @param instructionProcessor the InstructionProcessor to process {@link Instruction instructions} with the
     *                             given command.
     */
    public void putInstructionProcessor(String commandToListenTo, InstructionProcessor instructionProcessor) {
        instructionProcessorCollection.putIfNotNull(commandToListenTo, instructionProcessor);
    }

    /**
     * Gets the responsible {@link InstructionProcessor instructionProcessor} for the given commandToListenTo.
     *
     * @param commandToListenTo the command the instructionProcessor is listening to
     * @return the InstructionProcessor that would be used to process the {@link Instruction instruction} for the
     * given command or null, if there is no {@link InstructionProcessor instructionProcessor} for this command.
     */
    public InstructionProcessor getInstructionProcessor(String commandToListenTo) {
        return instructionProcessorCollection.get(commandToListenTo);
    }

    /**
     * Removes the {@link InstructionProcessor instructionProcessor} for the given commandToListenTo.
     *
     * @param commandToListenTo the command the instructionProcessor is listening to
     * @return the InstructionProcessor that used to listen to the given command or null
     */
    public InstructionProcessor removeInstructionProcessor(String commandToListenTo) {
        return instructionProcessorCollection.remove(commandToListenTo);
    }

    /**
     * Removes all explicitly set {@link InstructionProcessor instructionProcessors}.
     */
    public void removeAllInstructionProcessors() {
        instructionProcessorCollection.clear();
    }

    /**
     * Processes the {@link Instruction instruction} by dispatching it to a {@link InstructionDispatcher
     * instructionDispatcher}.
     *
     * @param instructionToProcess the instruction to process
     */
    public void processInstruction(Instruction instructionToProcess) throws NjamsInstructionException {
        InstructionDispatcher dispatcher = new InstructionDispatcher(instructionProcessorCollection,
                instructionToProcess);
        dispatcher.dispatchInstruction();
    }
}
