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
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FallbackProcessorTest extends AbstractInstructionProcessor{

    private FallbackProcessor fallbackProcessor;

    public FallbackProcessorTest(){
        fallbackProcessor = new FallbackProcessor();
    }

    @Test
    public void testNullInstruction(){
        fallbackProcessor.processInstruction(null);
    }

    @Test
    public void testNullRequest(){
        Instruction instruction = instructionBuilder.prepareGetLogLevelInstruction().build();
        instruction.setRequest(null);
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.REQUEST_IS_NULL);
    }

    private void checkForCorrectProcessing(Instruction instruction, String errorMessage) {
        Response response = instruction.getResponse();
        assertEquals(response.getResultMessage(), errorMessage);
        assertEquals(response.getResultCode(), FallbackProcessor.ERROR_RESULT_CODE);
        assertTrue(response.getParameters().isEmpty());
        assertEquals(response.getDateTime(), null);
    }

    @Test
    public void testNullCommand(){
        Instruction instruction = instructionBuilder.prepareGetLogLevelInstruction().build();
        instruction.getRequest().setCommand(null);
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_IS_NULL);
    }

    @Test
    public void testEmptyCommand(){
        Instruction instruction = instructionBuilder.prepareGetLogLevelInstruction().build();
        instruction.getRequest().setCommand("");
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_IS_EMPTY);
    }

    @Test
    public void testNormalCommand(){
        Instruction instruction = instructionBuilder.prepareGetLogLevelInstruction().build();
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_UNKNOWN + instruction.getRequest().getCommand());
    }
}