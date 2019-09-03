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
import com.im.njams.sdk.communication.receiver.instruction.entity.InstructionProcessorCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class InstructionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcher.class);

    protected InstructionProcessorCollection instructionProcessorCollection;

    public InstructionDispatcher() {
        this.instructionProcessorCollection = new InstructionProcessorCollection();
    }

    public void putInstructionProcessor(String commandToListenTo, InstructionProcessor instructionProcessor) {
        instructionProcessorCollection.putIfNotNull(commandToListenTo.toLowerCase(), instructionProcessor);
    }

    public InstructionProcessor getInstructionProcessor(String commandToListenTo) {
        return instructionProcessorCollection.get(commandToListenTo.toLowerCase());
    }

    public InstructionProcessor removeInstructionProcessor(String commandToListenTo) {
        return instructionProcessorCollection.remove(commandToListenTo.toLowerCase());
    }

    public void removeAllInstructionProcessors() {
        instructionProcessorCollection.clear();
    }

    public void dispatchInstruction(Instruction instructionToProcess) {
        String commandToProcess = extractLowerCaseCommandFrom(instructionToProcess);

        InstructionProcessor processorToExecute = getExecutingProcessorFor(commandToProcess);

        logDispatching(commandToProcess, processorToExecute);

        processInstructionWithProcessor(instructionToProcess, processorToExecute);
    }

    protected String extractLowerCaseCommandFrom(Instruction instruction) {
        return instruction.getRequestReader().getCommand().toLowerCase();
    }

    protected InstructionProcessor getExecutingProcessorFor(String commandToProcess) {
        InstructionProcessor matchingProcessor = instructionProcessorCollection.get(commandToProcess);
        if (matchingProcessor == null) {
            matchingProcessor = instructionProcessorCollection.getDefault();
        }
        return matchingProcessor;
    }

    protected void logDispatching(String commandToProcess, InstructionProcessor executingProcessor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching instruction with command {} to {}", commandToProcess,
                    executingProcessor.getClass().getSimpleName());
        }
    }

    protected void processInstructionWithProcessor(Instruction instructionToProcess,
            InstructionProcessor processorToExecute) {
        processorToExecute.processInstruction(instructionToProcess);
    }
}
