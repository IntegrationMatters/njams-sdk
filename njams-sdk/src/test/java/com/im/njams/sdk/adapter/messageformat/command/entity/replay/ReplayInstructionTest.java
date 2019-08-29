/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class ReplayInstructionTest {

    private static final String PROCESS_KEY = "Process";
    private static final String PROCESS_VALUE = "process";
    private static final String START_ACTIVITY_KEY = "StartActivity";
    private static final String START_ACTIVITY_VALUE = "startActivity";
    private static final String PAYLOAD_KEY = "Payload";
    private static final String PAYLOAD_VALUE = "payload";
    private static final String DEEPTRACE_KEY = "Deeptrace";
    private static final String DEEPTRACE_VALUE = "true";
    private static final String TEST_KEY = "Test";
    private static final String TEST_VALUE = "true";

    private static final String EXCEPTION_KEY = "Exception";
    private static final String EXCEPTION_VALUE = "Ex";
    private static final String MAIN_LOG_ID_KEY = "MainLogId";
    private static final String MAIN_LOG_ID_VALUE = "mainLogId";

    private Instruction instructionMock;

    private Request requestMock;

    private Response responseMock;

    private ReplayInstruction replayInstruction;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
        requestMock = mock(Request.class);
        responseMock = mock(Response.class);
        fillRequestAndResponse(requestMock, responseMock);
        replayInstruction = spy(new ReplayInstruction(instructionMock));
    }

    private void fillRequestAndResponse(Request request, Response response) {
        when(instructionMock.getRequest()).thenReturn(request);
        when(instructionMock.getResponse()).thenReturn(response);
    }

//CreateRequestReader tests

    @Test
    public void createReplayRequestReader() {
        ReplayRequestReader requestReaderInstance = replayInstruction
                .createRequestReaderInstance(mock(Request.class));
        assertTrue(requestReaderInstance instanceof ReplayRequestReader);
    }

    @Test
    public void createRequestReaderInstanceWithNullRequest() {
        ReplayRequestReader requestReaderInstance = replayInstruction
                .createRequestReaderInstance(null);
        assertTrue(requestReaderInstance instanceof ReplayRequestReader);
    }

//CreateResponseWriter tests

    @Test
    public void createReplayResponseWriter() {
        ReplayResponseWriter responseWriterInstance = replayInstruction
                .createResponseWriterInstance(mock(Response.class));
        assertTrue(responseWriterInstance instanceof ReplayResponseWriter);
    }

    @Test
    public void createResponseWriterInstanceWithNullResponse() {
        ReplayResponseWriter responseWriterInstance = replayInstruction
                .createResponseWriterInstance(null);
        assertTrue(responseWriterInstance instanceof ReplayResponseWriter);
    }

//ReplayRequestReader tests

    @Test
    public void getProcess(){
        fillRequestParameters();
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertEquals(PROCESS_VALUE, requestReader.getProcess());
    }

    private void fillRequestParameters(){
        Map<String, String> params = new HashMap<>();
        when(requestMock.getParameters()).thenReturn(params);
        params.put(PROCESS_KEY, PROCESS_VALUE);
        params.put(START_ACTIVITY_KEY, START_ACTIVITY_VALUE);
        params.put(PAYLOAD_KEY, PAYLOAD_VALUE);
        params.put(DEEPTRACE_KEY, DEEPTRACE_VALUE);
        params.put(TEST_KEY, TEST_VALUE);
    }

    @Test
    public void getStartActivity(){
        fillRequestParameters();
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertEquals(START_ACTIVITY_VALUE, requestReader.getStartActivity());
    }

    @Test
    public void getPayload(){
        fillRequestParameters();
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertEquals(PAYLOAD_VALUE, requestReader.getPayload());
    }

    @Test
    public void isDeeptrace(){
        fillRequestParameters();
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertTrue(requestReader.isDeepTrace());
    }
    @Test
    public void isDeeptraceNotSet(){
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertFalse(requestReader.isDeepTrace());
    }

    @Test
    public void isTest(){
        fillRequestParameters();
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertTrue(requestReader.getTest());
    }

    @Test
    public void isTestNotSet(){
        ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertFalse(requestReader.getTest());
    }

//ReplayResponseWriter tests

    @Test
    public void setException(){
        ReplayResponseWriter responseWriter = replayInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setException(EXCEPTION_VALUE);
        verify(mockedMap).put(EXCEPTION_KEY, EXCEPTION_VALUE);
    }

    private Map<String, String> setMockedMap(){
        Map<String, String> mockedMap = mock(Map.class);
        when(responseMock.getParameters()).thenReturn(mockedMap);
        return mockedMap;
    }

    @Test
    public void setMainLogId(){
        ReplayResponseWriter responseWriter = replayInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setMainLogId(MAIN_LOG_ID_VALUE);
        verify(mockedMap).put(MAIN_LOG_ID_KEY, MAIN_LOG_ID_VALUE);
    }
}