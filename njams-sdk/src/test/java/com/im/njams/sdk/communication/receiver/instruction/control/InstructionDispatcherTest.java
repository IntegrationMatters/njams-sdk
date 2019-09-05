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
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.InstructionProcessor;
import com.im.njams.sdk.communication.receiver.instruction.entity.InstructionProcessorCollection;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class InstructionDispatcherTest {

    private static final String COMMAND = "TeSt";

    private InstructionProcessorCollection instructionProcessorCollection;

    private Instruction instructionMockWithoutRequestReader;

    private Instruction instructionMockWithoutCommand;

    private Instruction.RequestReader requestReaderWithoutCommand;

    private Instruction instructionMock;

    private Instruction.RequestReader requestReaderMock;

    private InstructionDispatcher instructionDispatcher;

    private Instruction.ResponseWriter responseWriterMock;

    @Before
    public void initialize() throws NjamsInstructionException {
        instructionMockWithoutRequestReader = mock(Instruction.class);

        instructionMockWithoutCommand = mock(Instruction.class);
        requestReaderWithoutCommand = mock(Instruction.RequestReader.class);
        responseWriterMock = mock(Instruction.ResponseWriter.class);
        when(instructionMockWithoutCommand.getRequestReader()).thenReturn(requestReaderWithoutCommand);
        when(instructionMockWithoutCommand.getResponseWriter()).thenReturn(responseWriterMock);
        when(responseWriterMock.setResultMessage(any())).thenReturn(responseWriterMock);
        when(responseWriterMock.setResultCode(any())).thenReturn(responseWriterMock);
        when(responseWriterMock.setParameters(any())).thenReturn(responseWriterMock);

        instructionMock = mock(Instruction.class);
        requestReaderMock = mock(Instruction.RequestReader.class);
        when(instructionMock.getRequestReader()).thenReturn(requestReaderMock);
        when(requestReaderMock.getCommand()).thenReturn(COMMAND);
        when(instructionMock.getResponseWriter()).thenReturn(responseWriterMock);

        instructionProcessorCollection = new InstructionProcessorCollection();
        instructionDispatcher = new InstructionDispatcher(instructionProcessorCollection, instructionMock);
    }

//Constructor tests

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithoutProcessorCollectionAndWithoutInstruction() throws NjamsInstructionException {
        new InstructionDispatcher(null, null);
    }

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithoutProcessorCollectionAndWithNullRequest() throws NjamsInstructionException {
        new InstructionDispatcher(null, instructionMockWithoutRequestReader);
    }

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithoutProcessorCollectionAndWithNullCommand() throws NjamsInstructionException {
        new InstructionDispatcher(null, instructionMockWithoutCommand);
    }

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithoutProcessorCollectionAndNormalInstruction() throws NjamsInstructionException {
        new InstructionDispatcher(null, instructionMock);
    }

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithoutInstruction() throws NjamsInstructionException {
        new InstructionDispatcher(instructionProcessorCollection, null);
    }

    @Test(expected = NjamsInstructionException.class)
    public void initializeWithNullRequest() throws NjamsInstructionException {
        new InstructionDispatcher(instructionProcessorCollection, instructionMockWithoutRequestReader);
    }

//DispatchInstruction tests

    @Test
    public void dispatchNullCommand() throws NjamsInstructionException {
        instructionDispatcher = new InstructionDispatcher(instructionProcessorCollection,
                instructionMockWithoutCommand);
        when(requestReaderWithoutCommand.isCommandNull()).thenReturn(true);
        instructionDispatcher.dispatchInstruction();
        verify(responseWriterMock).setResultCode(ResultCode.WARNING);
    }

    @Test
    public void dispatchEmptyCommand() throws NjamsInstructionException {
        instructionDispatcher = new InstructionDispatcher(instructionProcessorCollection,
                instructionMock);
        when(requestReaderWithoutCommand.isCommandEmpty()).thenReturn(true);
        instructionDispatcher.dispatchInstruction();
        verify(responseWriterMock).setResultCode(ResultCode.WARNING);
    }

    @Test
    public void dispatchUnknownCommand() throws NjamsInstructionException {
        instructionDispatcher = new InstructionDispatcher(instructionProcessorCollection,
                instructionMock);
        instructionDispatcher.dispatchInstruction();
        verify(responseWriterMock).setResultCode(ResultCode.WARNING);
    }

    @Test
    public void dispatchWithNormalCommand() throws NjamsInstructionException {
        InstructionProcessor instructionProcessor = new InstructionProcessorImpl();
        instructionProcessorCollection.putIfNotNull(COMMAND, instructionProcessor);
        instructionDispatcher = new InstructionDispatcher(instructionProcessorCollection,
                instructionMock);
        instructionDispatcher.dispatchInstruction();
        verify(responseWriterMock).setResultCode(ResultCode.SUCCESS);
    }

    private class InstructionProcessorImpl implements InstructionProcessor{

        @Override
        public String getCommandToListenTo() {
            return COMMAND;
        }

        @Override
        public void processInstruction(Instruction instructionToProcess) {
            instructionToProcess.getResponseWriter().setResultCode(ResultCode.SUCCESS);
        }
    }
}