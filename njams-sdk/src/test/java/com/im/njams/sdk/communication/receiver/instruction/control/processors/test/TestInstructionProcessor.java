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

package com.im.njams.sdk.communication.receiver.instruction.control.processors.test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.communication.receiver.instruction.control.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestInstructionProcessor implements InstructionProcessor {

    public static final String TEST_COMMAND = Command.TEST_EXPRESSION.commandString();

    private static final Logger LOG = LoggerFactory.getLogger(TestInstructionProcessor.class);

    private static InstructionProcessor processor = null;

    public static void setInstructionProcessorMock(InstructionProcessor instructionProcessor) {
        processor = instructionProcessor;
    }

    @Override
    public String getCommandToListenTo() {
        if (processor != null) {
            return processor.getCommandToListenTo();
        } else {
            return TEST_COMMAND;
        }
    }

    @Override
    public void processInstruction(Instruction instruction) {
        if (processor != null) {
            processor.processInstruction(instruction);
        } else {
            LOG.debug("processInstruction in TestInstructionProcessor has been invoked.");
        }
    }
}
