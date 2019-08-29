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
package com.im.njams.sdk.communication.instruction.control.processors;

import com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.instruction.control.templates.DefaultProcessorTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class FallbackProcessor extends DefaultProcessorTemplate {

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

    String warningMessage = DefaultRequestReader.EMPTY_STRING;

    @Override
    protected void processDefaultInstruction() {
        clear();

        InstructionProblem instructionProblem = checkTypeOfProblem();

        handleProblem(instructionProblem);
    }

    private void clear() {
        warningMessage = DefaultRequestReader.EMPTY_STRING;
    }

    private InstructionProblem checkTypeOfProblem() {
        InstructionProblem problemType = InstructionProblem.COMMAND_IS_UNKNOWN;

        if (getInstruction().isEmpty()) {
            problemType = InstructionProblem.INSTRUCTION_IS_NULL;
        } else {
            final DefaultRequestReader requestReader = getDefaultRequestReader();
            if (requestReader.isEmpty()) {
                problemType = InstructionProblem.REQUEST_IS_NULL;
            } else if (requestReader.isCommandNull()) {
                problemType = InstructionProblem.COMMAND_IS_NULL;
            } else if (requestReader.isCommandEmpty()) {
                problemType = InstructionProblem.COMMAND_IS_EMPTY;
            }
        }
        return problemType;
    }

    private void handleProblem(InstructionProblem instructionProblem) {
        warningMessage = createWarningMessageFrom(instructionProblem);
    }

    private String createWarningMessageFrom(InstructionProblem instructionProblem) {
        String warningMessageToReturn = instructionProblem.getMessage();

        if (instructionProblem == instructionProblem.COMMAND_IS_UNKNOWN) {
            warningMessageToReturn = warningMessageToReturn.concat(getDefaultRequestReader().getCommand());
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
        return !getInstruction().isEmpty();
    }

    private void setResponse() {
        getDefaultResponseWriter().
                setResultCodeAndResultMessage(ResultCode.WARNING, warningMessage);
    }

    @Override
    protected void logProcessing() {
        if (LOG.isWarnEnabled()) {
            LOG.warn(warningMessage);
        }
    }
}
