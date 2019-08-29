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

package com.im.njams.sdk.adapter.messageformat.command.entity.defaults;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultInstructionTest {

    private Instruction instructionMock;

    private Request requestMock;

    private Response responseMock;

    private DefaultInstruction defaultInstruction;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
        requestMock = mock(Request.class);
        responseMock = mock(Response.class);
        fillRequestAndResponse(requestMock, responseMock);
        defaultInstruction = spy(new DefaultInstruction(instructionMock));
    }

    private void fillRequestAndResponse(Request request, Response response) {
        when(instructionMock.getRequest()).thenReturn(request);
        when(instructionMock.getResponse()).thenReturn(response);
    }

//CreateRequestReader tests

    @Test
    public void createRequestReaderInstanceCreatesDefaultRequestReader() {
        DefaultRequestReader requestReader = defaultInstruction.createRequestReaderInstance(mock(Request.class));
        assertTrue(requestReader instanceof DefaultRequestReader);
    }

    @Test
    public void createRequestReaderInstanceWithNullRequest() {
        DefaultRequestReader requestReader = defaultInstruction.createRequestReaderInstance(null);
        assertTrue(requestReader instanceof DefaultRequestReader);
    }

//CreateResponseWriter tests

    @Test
    public void createResponseWriterInstanceCreatesDefaultResponseWriter() {
        DefaultResponseWriter responseWriter = defaultInstruction.createResponseWriterInstance(mock(Response.class));
        assertTrue(responseWriter instanceof DefaultResponseWriter);
    }

    @Test
    public void createResponseWriterInstanceWithNullResponse() {
        DefaultResponseWriter responseWriter = defaultInstruction.createResponseWriterInstance(null);
        assertTrue(responseWriter instanceof DefaultResponseWriter);
    }
}