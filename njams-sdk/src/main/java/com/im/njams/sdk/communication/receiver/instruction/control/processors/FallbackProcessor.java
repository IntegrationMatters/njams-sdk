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
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.AbstractProcessorTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader.EMPTY_STRING;

/**
 * Todo: Write Doc
 */
public class FallbackProcessor extends AbstractProcessorTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProcessor.class);

    private static final String EMPTY_COMMAND = "";

    @Override
    public String getCommandToListenTo() {
        return EMPTY_COMMAND;
    }

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

    String warningMessage = EMPTY_STRING;

    @Override
    protected void process() {
        clear();

        InstructionProblem instructionProblem = checkTypeOfProblem();

        handleProblem(instructionProblem);

        setWarningResponse();
    }

    private void clear() {
        warningMessage = EMPTY_STRING;
    }

    private InstructionProblem checkTypeOfProblem() {
        InstructionProblem problemType = InstructionProblem.COMMAND_IS_UNKNOWN;

        if (getInstruction().isEmpty()) {
            problemType = InstructionProblem.INSTRUCTION_IS_NULL;
        } else {
            final Instruction.RequestReader requestReader = getRequestReader();
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
            warningMessageToReturn = warningMessageToReturn.concat(getRequestReader().getCommand());
        }

        return warningMessageToReturn;
    }

    private void setWarningResponse() {
        if (!getInstruction().isEmpty()) {
            getResponseWriter().setResultCode(ResultCode.WARNING).setResultMessage(warningMessage);
        }
    }

    @Override
    protected void logProcessing() {
        if (LOG.isWarnEnabled()) {
            LOG.warn(warningMessage);
        }
    }
}
