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
package com.im.njams.sdk.communication.instruction.control.processor.fallback;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication.instruction.control.processor.AbstractInstructionProcessor;
import com.im.njams.sdk.communication.instruction.util.InstructionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class FallbackProcessor extends AbstractInstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProcessor.class);

    enum InstructionProblem {
        INSTRUCTION_IS_NULL("Instruction is null."),
        REQUEST_IS_NULL("Instruction has a null request."),
        COMMAND_IS_NULL("Instruction has a null command."),
        COMMAND_IS_EMPTY("Instruction has an empty command."),
        COMMAND_IS_UNKNOWN("Instruction has an unknown command: ");

        private String message;

        InstructionProblem(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    String warningMessage;

    public FallbackProcessor(){
        super();
        setBackToStart();
    }

    private void setBackToStart(){
        warningMessage = InstructionWrapper.EMPTY_STRING;
    }

    @Override
    protected void prepareProcessing(Instruction instruction) {
        super.prepareProcessing(instruction);
        setBackToStart();
    }

    @Override
    protected void process() {
        InstructionProblem instructionProblem = checkTypeOfProblem();

        handleProblem(instructionProblem);
    }

    private InstructionProblem checkTypeOfProblem() {
        InstructionProblem problemType = InstructionProblem.COMMAND_IS_UNKNOWN;
        if (getInstructionWrapper().isInstructionNull()) {
            problemType = InstructionProblem.INSTRUCTION_IS_NULL;
        } else if (getInstructionWrapper().isRequestNull()) {
            problemType = InstructionProblem.REQUEST_IS_NULL;
        } else if (getInstructionWrapper().isCommandNull()) {
            problemType = InstructionProblem.COMMAND_IS_NULL;
        } else if (getInstructionWrapper().isCommandEmpty()) {
            problemType = InstructionProblem.COMMAND_IS_EMPTY;
        }
        return problemType;
    }

    private void handleProblem(InstructionProblem instructionProblem) {
        warningMessage = createWarningMessageFrom(instructionProblem);
    }

    private String createWarningMessageFrom(InstructionProblem instructionProblem) {
        String warningMessageToReturn = instructionProblem.getMessage();

        if (instructionProblem == instructionProblem.COMMAND_IS_UNKNOWN) {
            warningMessageToReturn = warningMessageToReturn.concat(getInstructionWrapper().getCommandOrEmptyString());
        }

        return warningMessageToReturn;
    }

    @Override
    protected void setInstructionResponse() {
        if (canResponseBeSet()) {
            setResponse();
        }
    }

    private boolean canResponseBeSet() {
        return !getInstructionWrapper().isInstructionNull();
    }

    private void setResponse() {
        getInstructionWrapper().createResponseForInstruction(InstructionWrapper.WARNING_RESULT_CODE, warningMessage);
    }

    @Override
    protected void logFinishedProcessing() {
        if (LOG.isWarnEnabled()) {
            LOG.warn(warningMessage);
        }
    }
}
