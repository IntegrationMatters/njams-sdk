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

package com.im.njams.sdk.communication.receiver.instruction.control.templates;

import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.DefaultProcessorTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class DefaultProcessorTemplateTest {

    private DefaultProcessorTemplate defaultProcessorTemplate;

    private NjamsInstruction njamsInstructionMock;

    private NjamsRequestReader njamsRequestReaderMock;

    private NjamsResponseWriter njamsResponseWriterMock;

    @Before
    public void initialize() {
        defaultProcessorTemplate = spy(new DefaultProcessorTemplateImpl());
        njamsInstructionMock = mock(NjamsInstruction.class);
        njamsRequestReaderMock = mock(NjamsRequestReader.class);
        njamsResponseWriterMock = mock(NjamsResponseWriter.class);
        doReturn(njamsInstructionMock).when(defaultProcessorTemplate).getInstruction();
        when(njamsInstructionMock.getRequestReader()).thenReturn(njamsRequestReaderMock);
        when(njamsInstructionMock.getResponseWriter()).thenReturn(njamsResponseWriterMock);
    }

//Process tests

    @Test
    public void processCallsWorkNormally() {
        defaultProcessorTemplate.process();
        verify(defaultProcessorTemplate).processDefaultInstruction();
        verify(defaultProcessorTemplate).setInstructionResponse();
    }

//GetDefaultRequestReader tests

    @Test
    public void getDefaultRequestReaderReturnsARequestReaderTypeObject() {
        assertTrue(defaultProcessorTemplate.getDefaultRequestReader() instanceof NjamsRequestReader);
    }

//GetDefaultResponseWriter tests

    @Test
    public void getDefaultResponseWriterReturnsAResponseWriterTypeObject() {
        assertTrue(defaultProcessorTemplate.getDefaultResponseWriter() instanceof NjamsResponseWriter);
    }

//Private helper classes

    private class DefaultProcessorTemplateImpl extends DefaultProcessorTemplate {

        @Override
        protected void processDefaultInstruction() {
            //Do nothing
        }

        @Override
        protected void setInstructionResponse() {
            //Do nothing
        }
    }
}