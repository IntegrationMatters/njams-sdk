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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides functionality to dispatch an incoming {@link Instruction instruction} to the given
 * {@link InstructionProcessorCollection instructionProcessors}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
class InstructionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionDispatcher.class);

    private static final String NJAMS_INSTRUCTION_IS_NULL_EXCEPTION = "NjamsInstruction must not be null";

    private static final String REQUEST_READER_IS_NULL_EXCEPTION = "RequestReader must not be null";

    private static final String NO_INSTRUCTION_PROCESSORS_TO_DISPATCH_TO = "InstructionProcessorCollection must not be null";

    private Instruction instructionToProcess;

    private String lowerCaseCommandToProcess;

    private InstructionProcessor processorToExecute;

    /**
     * The constructor sets the {@link Instruction instruction} to process, extracts the {@link String command} from the
     * {@link Instruction instruction} and determines the {@link InstructionProcessor instructionProcessor} that is
     * responsible for processing the instruction.
     *
     * @param processorsToDispatchTo the collection of instruction processors to choose from for dispatching
     * @param instructionToProcess   the instruction to process
     * @throws NjamsInstructionException when {@link Instruction instructionToProcess} or its
     *                                   {@link Instruction.RequestReader requestReader} is null.
     */
    InstructionDispatcher(InstructionProcessorCollection processorsToDispatchTo, Instruction instructionToProcess)
            throws NjamsInstructionException {
        this.instructionToProcess = instructionToProcess;
        this.lowerCaseCommandToProcess = extractLowerCaseCommand();
        this.processorToExecute = extractExecutingProcessor(processorsToDispatchTo);
    }

    private String extractLowerCaseCommand() throws NjamsInstructionException {
        Instruction.RequestReader requestReader = getRequestReader();
        return extractCommand(requestReader);
    }

    private Instruction.RequestReader getRequestReader() throws NjamsInstructionException {
        try {
            return instructionToProcess.getRequestReader();
        } catch (NullPointerException instructionIsNullException) {
            throw new NjamsInstructionException(NJAMS_INSTRUCTION_IS_NULL_EXCEPTION, instructionIsNullException);
        }
    }

    private String extractCommand(Instruction.RequestReader requestReader) throws NjamsInstructionException {
        try {
            return requestReader.getCommand();
        } catch (NullPointerException requestReaderIsNullException) {
            throw new NjamsInstructionException(REQUEST_READER_IS_NULL_EXCEPTION, requestReaderIsNullException);
        }
    }

    private InstructionProcessor extractExecutingProcessor(InstructionProcessorCollection processorsToDispatchTo)
            throws NjamsInstructionException {
        try {
            InstructionProcessor matchingProcessor = processorsToDispatchTo.get(lowerCaseCommandToProcess);
            if (matchingProcessor == null) {
                matchingProcessor = processorsToDispatchTo.getDefault();
            }
            return matchingProcessor;
        }catch(NullPointerException noProcessorsToDispatchTo){
            throw new NjamsInstructionException(NO_INSTRUCTION_PROCESSORS_TO_DISPATCH_TO, noProcessorsToDispatchTo);
        }
    }

    /**
     * Processes the {@link InstructionDispatcher#instructionToProcess instruction} by dispatching it to the
     * {@link InstructionDispatcher#processorToExecute} which processes it.
     */
    public void dispatchInstruction() {
        logDispatching();

        processInstructionWithProcessor();
    }

    private void logDispatching() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching instruction with command {} to {}", lowerCaseCommandToProcess,
                    processorToExecute.getClass().getSimpleName());
        }
    }

    private void processInstructionWithProcessor() {
        processorToExecute.processInstruction(instructionToProcess);
    }
}

