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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SEND_PROJECTMESSAGE;
import static com.im.njams.sdk.communication.receiver.instruction.control.processors.SendProjectMessageProcessor.SUCCESS_RESULT_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SendProjectMessageProcessorTest {

    private SendProjectMessageProcessor sendProjectMessageProcessor;

    private Njams njamsMock = mock(Njams.class);

    @Before
    public void initialize() {
        sendProjectMessageProcessor = spy(new SendProjectMessageProcessor(njamsMock));
    }

//ProcessDefaultInstruction tests

    @Test
    public void getCommandToListenToIsCorrect(){
        assertEquals(SEND_PROJECTMESSAGE.commandString(), sendProjectMessageProcessor.getCommandToListenTo());
    }

    @Test
    public void testProcessDefaultInstruction() {
        NjamsResponseWriter defaultResponseWriterMock = mock(NjamsResponseWriter.class);
        doReturn(defaultResponseWriterMock).when(sendProjectMessageProcessor).getResponseWriter();
        sendProjectMessageProcessor.process();
        verify(njamsMock).flushResources();
        verify(defaultResponseWriterMock).setResultCodeAndResultMessage(ResultCode.SUCCESS, SUCCESS_RESULT_MESSAGE);

    }
}