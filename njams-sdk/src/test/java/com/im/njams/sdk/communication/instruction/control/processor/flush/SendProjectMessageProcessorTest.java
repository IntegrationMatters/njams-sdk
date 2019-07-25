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
package com.im.njams.sdk.communication.instruction.control.processor.flush;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class SendProjectMessageProcessorTest {

    private SendProjectMessageProcessor sendProjectMessageProcessor;

    private Njams njamsMock = mock(Njams.class);

    @Before
    public void initialize() {
        sendProjectMessageProcessor = spy(new SendProjectMessageProcessor(njamsMock));
    }

//Process tests

    @Test
    public void testProcess() {
        sendProjectMessageProcessor.process();
        verify(njamsMock).flushResources();
    }

//SetInstructionResponse tests

    @Test
    public void testSetInstructionResponse() {
        Instruction instructionMock = mock(Instruction.class);
        ResponseWriter responseWriterMock = mock(ResponseWriter.class);
        doReturn(instructionMock).when(sendProjectMessageProcessor).getInstruction();
        when(instructionMock.getResponseWriter()).thenReturn(responseWriterMock);
        when(responseWriterMock.setResultCode(any())).thenReturn(responseWriterMock);
        when(responseWriterMock.setResultMessage(any())).thenReturn(responseWriterMock);

        sendProjectMessageProcessor.setInstructionResponse();

        verify(responseWriterMock).setResultCode(ResponseWriter.ResultCode.SUCCESS);
        verify(responseWriterMock).setResultMessage(SendProjectMessageProcessor.SUCCESS_RESULT_MESSAGE);
    }
}