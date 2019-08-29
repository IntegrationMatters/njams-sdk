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

package com.im.njams.sdk.adapter.messageformat.command.entity.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReplayResponseWriterTest {

    private static final String EXCEPTION_KEY = "Exception";
    private static final String EXCEPTION_VALUE = "Ex";
    private static final String MAIN_LOG_ID_KEY = "MainLogId";
    private static final String MAIN_LOG_ID_VALUE = "mainLogId";

    private Response responseMock;

    private ReplayResponseWriter replayResponseWriter;

    @Before
    public void initialize() {
        responseMock = mock(Response.class);
        replayResponseWriter = spy(new ReplayResponseWriter(responseMock));
    }

//SetException tests

    @Test
    public void setExceptionCallsSuperMethods() {
        replayResponseWriter.setException(EXCEPTION_VALUE);

        verify(replayResponseWriter).putParameter(EXCEPTION_KEY, EXCEPTION_VALUE);
    }

    @Test
    public void setExceptionReturnsThis() {
        ReplayResponseWriter returnedReplayResponseWriter = replayResponseWriter.setException(EXCEPTION_VALUE);

        assertEquals(replayResponseWriter, returnedReplayResponseWriter);
    }

//SetMainLogId tests

    @Test
    public void setMainLogIdCallsSuperMethods() {
        replayResponseWriter.setMainLogId(MAIN_LOG_ID_VALUE);

        verify(replayResponseWriter).putParameter(MAIN_LOG_ID_KEY, MAIN_LOG_ID_VALUE);
    }

    @Test
    public void setMainLogIdReturnsThis() {
        ReplayResponseWriter returnedReplayResponseWriter = replayResponseWriter.setMainLogId(MAIN_LOG_ID_VALUE);

        assertEquals(replayResponseWriter, returnedReplayResponseWriter);
    }
}