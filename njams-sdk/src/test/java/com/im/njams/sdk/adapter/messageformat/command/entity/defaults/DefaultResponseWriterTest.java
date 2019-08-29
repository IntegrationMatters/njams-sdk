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

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;

public class DefaultResponseWriterTest {

    private static final ResultCode TEST_RESULT_CODE = ResultCode.SUCCESS;
    private static final String TEST_RESULT_MESSAGE = "test";

    private Response responseMock;

    private DefaultResponseWriter defaultResponseWriter;

    @Before
    public void initialize() {
        responseMock = mock(Response.class);
        defaultResponseWriter = spy(new DefaultResponseWriter(responseMock));
    }

//SetResultCodeAndResultMessage tests

    @Test
    public void CallingSuperMethods() {
        defaultResponseWriter.setResultCodeAndResultMessage(TEST_RESULT_CODE, TEST_RESULT_MESSAGE);

        verify(defaultResponseWriter).setResultCode(TEST_RESULT_CODE);
        verify(defaultResponseWriter).setResultMessage(TEST_RESULT_MESSAGE);
    }

    @Test
    public void returnsThis() {
        DefaultResponseWriter returnedResponseWriter = defaultResponseWriter
                .setResultCodeAndResultMessage(TEST_RESULT_CODE, TEST_RESULT_MESSAGE);

        assertEquals(defaultResponseWriter, returnedResponseWriter);
    }
}