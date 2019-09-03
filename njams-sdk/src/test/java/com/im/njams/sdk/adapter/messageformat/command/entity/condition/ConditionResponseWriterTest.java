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

package com.im.njams.sdk.adapter.messageformat.command.entity.condition;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstructionTest.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

public class ConditionResponseWriterTest {

    private Instruction instructionMock;

    private Response responseMock;

    private ConditionResponseWriter responseWriter;

    @Before
    public void initialize(){
        instructionMock = mock(Instruction.class);
        responseMock = mock(Response.class);
        when(instructionMock.getResponse()).thenReturn(responseMock);
        responseWriter = new ConditionResponseWriter(instructionMock);
    }
//ReplayResponseWriter tests

    @Test
    public void setExtract() throws NjamsInstructionException {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setExtract(EXTRACT);
        verify(mockedMap).put(EXTRACT_KEY, EXTRACT_VALUE);
    }

    @Test
    public void setNullExtract() throws NjamsInstructionException {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setExtract(null);
        verify(mockedMap).put(EXTRACT_KEY, "null");
    }

    private Map<String, String> setMockedMap() {
        Map<String, String> mockedMap = mock(Map.class);
        when(responseMock.getParameters()).thenReturn(mockedMap);
        return mockedMap;
    }

    @Test
    public void setLogMode() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogMode(LOG_MODE);
        verify(mockedMap).put(LOG_MODE_KEY, LOG_MODE_VALUE);
    }

    @Test
    public void setNullLogMode() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogMode(null);
        verify(mockedMap).put(LOG_MODE_KEY, null);
    }

    @Test
    public void setLogLevel() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogLevel(LOG_LEVEL);
        verify(mockedMap).put(LOG_LEVEL_KEY, LOG_LEVEL_VALUE);
    }

    @Test
    public void setNullLogLevel() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogLevel(null);
        verify(mockedMap).put(LOG_LEVEL_KEY, null);
    }

    @Test
    public void setExcluded() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setExcluded(EXCLUDE);
        verify(mockedMap).put(EXCLUDE_KEY, EXCLUDE_VALUE);
    }

    @Test
    public void setStartTime() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setStartTime(START_TIME);
        verify(mockedMap).put(START_TIME_KEY, START_TIME_VALUE);
    }

    @Test
    public void setNullStartTime() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setStartTime(null);
        verify(mockedMap).put(START_TIME_KEY, null);
    }

    @Test
    public void setEndTime() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setEndTime(END_TIME);
        verify(mockedMap).put(END_TIME_KEY, END_TIME_VALUE);
    }

    @Test
    public void setNullEndTime() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setEndTime(null);
        verify(mockedMap).put(END_TIME_KEY, null);
    }

    @Test
    public void setIterations() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setIterations(ITERATIONS);
        verify(mockedMap).put(ITERATIONS_KEY, ITERATIONS_VALUE);
    }

    @Test
    public void setDeepTrace() {
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setDeepTrace(DEEP_TRACE);
        verify(mockedMap).put(DEEP_TRACE_KEY, DEEP_TRACE_VALUE);
    }

}