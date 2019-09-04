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

package com.im.njams.sdk.communication.receiver.instruction.boundary;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import net.sf.saxon.functions.Remove;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InstructionProcessorServiceTest {

    private static final String COMMAND = "test";

    private static final String DIFFERENT_COMMAND = "test2";

    private static final String EMPTY_COMMAND = "";

    private InstructionProcessorService instructionProcessorService;

    private InstructionProcessor instructionProcessorMock;

    private InstructionProcessor instructionProcessorWithEmptyCommand;

    private InstructionProcessor instructionProcessorWithNullCommand;

    private Instruction instruction;

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    @Before
    public void initialize() {
        instructionProcessorService = new InstructionProcessorService();
        instructionProcessorMock = mock(InstructionProcessor.class);
        instructionProcessorWithEmptyCommand = mock(InstructionProcessor.class);
        instructionProcessorWithNullCommand = mock(InstructionProcessor.class);
        when(instructionProcessorMock.getCommandToListenTo()).thenReturn(COMMAND);
        when(instructionProcessorWithEmptyCommand.getCommandToListenTo()).thenReturn(EMPTY_COMMAND);
        when(instructionProcessorWithNullCommand.getCommandToListenTo()).thenReturn(null);

        instructionProcessorService.addInstructionProcessor(instructionProcessorMock);

        messageFormatInstruction = new com.faizsiegeln.njams.messageformat.v4.command.Instruction();
        instruction = new NjamsInstruction(messageFormatInstruction);
    }

//AddInstructionProcessor tests

    @Test
    public void addNullInstructionProcessor() {
        //Nothing happens
        instructionProcessorService.addInstructionProcessor(null);
    }

    @Test
    public void addInstructionProcessorWithNullCommand() {
        instructionProcessorService.addInstructionProcessor(instructionProcessorWithNullCommand);
        assertNull(instructionProcessorService.getInstructionProcessor(null));
    }

    @Test
    public void addInstructionProcessorWithEmptyCommand() {
        instructionProcessorService.addInstructionProcessor(instructionProcessorWithEmptyCommand);
        assertNull(instructionProcessorService.getInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void addTheSameInstructionProcessorWithCommandTwice() {
        instructionProcessorService.addInstructionProcessor(instructionProcessorMock);
        assertEquals(instructionProcessorMock, instructionProcessorService.getInstructionProcessor(COMMAND));
    }

    @Test
    public void replaceInstructionProcessorWithTheSameCommand() {
        InstructionProcessor replacer = mock(InstructionProcessor.class);
        when(replacer.getCommandToListenTo()).thenReturn(COMMAND);
        instructionProcessorService.addInstructionProcessor(replacer);
        assertNotEquals(instructionProcessorMock, instructionProcessorService.getInstructionProcessor(COMMAND));
        assertEquals(replacer, instructionProcessorService.getInstructionProcessor(COMMAND));
    }

//GetInstructionProcessor tests

    @Test
    public void getInstructionProcessorWithNullCommand() {
        assertNull(instructionProcessorService.getInstructionProcessor(null));
    }

    @Test
    public void getInstructionProcessorWithEmptyCommand() {
        assertNull(instructionProcessorService.getInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void getInstructionProcessorWithCommand() {
        assertEquals(instructionProcessorMock, instructionProcessorService.getInstructionProcessor(COMMAND));
    }

//RemoveInstructionProcessor tests

    @Test
    public void removeInstructionProcessorWithNullCommand() {
        //Nothing happens
        instructionProcessorService.removeInstructionProcessor(null);
    }

    @Test
    public void removeInstructionProcessorWithEmptyCommand() {
        //Nothing happens
        instructionProcessorService.getInstructionProcessor(EMPTY_COMMAND);
    }

    @Test
    public void removeInstructionProcessorWithCommand() {
        instructionProcessorService.removeInstructionProcessor(COMMAND);
        assertNull(instructionProcessorService.getInstructionProcessor(COMMAND));
    }

//ProcessInstruction tests

    @Test
    public void processWithNullInstructionDoesntThrowAnException() {
        instructionProcessorService.processInstruction(null);
    }

    @Test
    public void processWithNullRequestDoesntThrowAnException() {
        instructionProcessorService.processInstruction(instruction);
    }

    @Test
    public void processWithDifferentCommandCantFindProcessor() {
        Request request = new Request();
        request.setCommand(DIFFERENT_COMMAND);
        messageFormatInstruction.setRequest(request);
        instructionProcessorService.processInstruction(instruction);

        assertEquals(1, messageFormatInstruction.getResponse().getResultCode());
    }

    @Test
    public void processNormally() {
        Request request = new Request();
        request.setCommand(COMMAND);
        messageFormatInstruction.setRequest(request);
        doAnswer((inst) -> {
            instruction.getResponseWriter().setResultCode(ResultCode.SUCCESS);
            return null;
        }).when(instructionProcessorMock).processInstruction(any());

        instructionProcessorService.processInstruction(instruction);
        assertEquals(0, messageFormatInstruction.getResponse().getResultCode());
    }

//Stop tests

    @Test
    public void testRemovesAllInstructionProcessors(){
        instructionProcessorService.stop();
        assertNull(instructionProcessorService.getInstructionProcessor(COMMAND));
    }
}