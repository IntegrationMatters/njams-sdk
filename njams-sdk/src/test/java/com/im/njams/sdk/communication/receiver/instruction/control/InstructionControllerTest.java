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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.InstructionProcessor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InstructionControllerTest {

    private static final String COMMAND_TO_LISTEN_TO = "TeSt";

    private static final String EMPTY_COMMAND = "";

    private InstructionController instructionController;

    private InstructionProcessor instructionProcessorMock;

    private Instruction instruction;

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    private Request request;


    @Before
    public void initialize() {
        instructionController = new InstructionController();
        instructionProcessorMock = mock(InstructionProcessor.class);
        doAnswer((invocationOnMock) -> {
            instruction.getResponseWriter().setResultCode(ResultCode.SUCCESS);
            return null;
        }).when(instructionProcessorMock).processInstruction(any());
        messageFormatInstruction = spy(new com.faizsiegeln.njams.messageformat.v4.command.Instruction());
        request = spy(new Request());
        request.setCommand(COMMAND_TO_LISTEN_TO);
        messageFormatInstruction.setRequest(request);
        instruction = spy(new NjamsInstruction(messageFormatInstruction));
        instructionController.putInstructionProcessor(COMMAND_TO_LISTEN_TO, instructionProcessorMock);
    }

//putInstructionProcessor tests

    @Test
    public void putNullInstructionProcessorWithNullCommand() {
        instructionController.putInstructionProcessor(null, null);
        assertNull(instructionController.getInstructionProcessor(null));
    }

    @Test
    public void putInstructionProcessorWithNullCommand() {
        instructionController.putInstructionProcessor(null, instructionProcessorMock);
        assertNull(instructionController.getInstructionProcessor(null));
    }

    @Test
    public void putNullInstructionProcessorWithEmptyCommand() {
        instructionController.putInstructionProcessor(EMPTY_COMMAND, null);
        assertNull(null, instructionController.getInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void putInstructionProcessorWithEmptyCommand() {
        instructionController.putInstructionProcessor(EMPTY_COMMAND, instructionProcessorMock);
        assertEquals(instructionProcessorMock, instructionController.getInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void putInstructionProcessorWithCommand() {
        assertEquals(instructionProcessorMock, instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

    @Test
    public void putNullInstructionProcessorWithCommandDoesntRemoveExistingInstructionProcessor() {
        instructionController.putInstructionProcessor(COMMAND_TO_LISTEN_TO, null);
        assertEquals(instructionProcessorMock, instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

    @Test
    public void putInstructionProcessorIgnoresCases() {
        assertEquals(instructionProcessorMock,
                instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO.toLowerCase()));
        assertEquals(instructionProcessorMock,
                instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO.toUpperCase()));
    }

//GetInstruction tests

    @Test
    public void getInstructionProcessorForNullCommand() {
        assertNull(instructionController.getInstructionProcessor(null));
    }

    @Test
    public void getInstructionProcessorForEmptyCommand() {
        assertNull(instructionController.getInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void getInstructionProcessorForNormalCommand() {
        assertEquals(instructionProcessorMock, instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

    @Test
    public void getInstructionProcessorIgnoresCases() {
        assertEquals(instructionProcessorMock,
                instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO.toUpperCase()));
        assertEquals(instructionProcessorMock,
                instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO.toLowerCase()));
    }

//RemoveInstructionProcessor tests

    @Test
    public void removeInstructionProcessorForNullCommand() {
        assertNull(instructionController.removeInstructionProcessor(null));
    }

    @Test
    public void removeInstructionProcessorForEmptyCommand() {
        assertNull(instructionController.removeInstructionProcessor(EMPTY_COMMAND));
    }

    @Test
    public void removeInstructionProcessorForNormalCommand() {
        assertEquals(instructionProcessorMock, instructionController.removeInstructionProcessor(COMMAND_TO_LISTEN_TO));
        assertNull(instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

    @Test
    public void removeInstructionProcessorIsntIdempotent() {
        assertEquals(instructionProcessorMock, instructionController.removeInstructionProcessor(COMMAND_TO_LISTEN_TO));
        assertNull(instructionController.removeInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

    @Test
    public void removeInstructionProcessorIgnoresUpperCase() {
        assertEquals(instructionProcessorMock,
                instructionController.removeInstructionProcessor(COMMAND_TO_LISTEN_TO.toUpperCase()));
    }

    @Test
    public void removeInstructionProcessorIgnoresLowerCase() {
        assertEquals(instructionProcessorMock,
                instructionController.removeInstructionProcessor(COMMAND_TO_LISTEN_TO.toLowerCase()));
    }

//RemoveAllInstructionProcessors tests

    @Test
    public void removeInstructionProcessorsRemoveAllExplicitlySetInstructionProcessors() {
        instructionController.removeAllInstructionProcessors();
        assertNull(instructionController.getInstructionProcessor(COMMAND_TO_LISTEN_TO));
    }

//ProcessInstruction tests

    @Test(expected = NjamsInstructionException.class)
    public void processNullInstructionThrowsNjamsInstructionException() throws NjamsInstructionException {
        instructionController.processInstruction(null);
    }

    @Test(expected = NjamsInstructionException.class)
    public void processInstructionWithoutRequestReaderThrowsNjamsInstructionException()
            throws NjamsInstructionException {
        doReturn(null).when(instruction).getRequestReader();
        instructionController.processInstruction(instruction);
    }

    @Test
    public void processInstructionWithNullCommand() throws NjamsInstructionException {
        request.setCommand(null);
        instructionController.processInstruction(instruction);
        assertEquals(1, messageFormatInstruction.getResponse().getResultCode());
    }

    @Test
    public void processInstructionWithEmptyCommand() throws NjamsInstructionException {
        request.setCommand("");
        instructionController.processInstruction(instruction);
        assertEquals(1, messageFormatInstruction.getResponse().getResultCode());
    }

    @Test
    public void processNormalCommand() throws NjamsInstructionException {
        instructionController.processInstruction(instruction);
        assertEquals(0, messageFormatInstruction.getResponse().getResultCode());
    }

}