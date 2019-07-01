/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class FallbackProcessor extends InstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackProcessor.class);

    /**
     * Todo: Write Doc
     */
    public static final String FALLBACK = "Fallback";

    protected static final String INSTRUCTION_IS_NULL = "Instruction should not be null";

    protected static final String REQUEST_IS_NULL = "Instruction should have a request";

    protected static final String COMMAND_IS_NULL = "Request should have a command";

    protected static final String COMMAND_IS_EMPTY = "Request should have a not empty command";

    protected static final String COMMAND_UNKNOWN = "Command is unknown: ";

    protected static final int ERROR_RESULT_CODE = 1;

    public FallbackProcessor() {
        super(FALLBACK);
    }

    @Override
    public void processInstruction(Instruction instruction) {
        String errorMessage;
        if (instruction == null) {
            errorMessage = INSTRUCTION_IS_NULL;
        } else {
            Request request = instruction.getRequest();
            if (request == null) {
                errorMessage = REQUEST_IS_NULL;
            } else {
                String command = request.getCommand();
                if (command == null) {
                    errorMessage = COMMAND_IS_NULL;
                } else if (command.equals("")) {
                    errorMessage = COMMAND_IS_EMPTY;
                } else {
                    errorMessage = COMMAND_UNKNOWN + command;
                }
            }
            Response response = new Response();
            response.setResultCode(ERROR_RESULT_CODE);
            response.setResultMessage(errorMessage);
            instruction.setResponse(response);
        }
        LOG.error(errorMessage);
    }
}
