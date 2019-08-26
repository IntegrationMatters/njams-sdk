package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
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
        ReplayInstruction.ReplayRequestReader requestReaderInstance = replayInstruction
                .createRequestReaderInstance(mock(Request.class));
        assertTrue(requestReaderInstance instanceof ReplayInstruction.ReplayRequestReader);
    }

    @Test
    public void createRequestReaderInstanceWithNullRequest() {
        ReplayInstruction.ReplayRequestReader requestReaderInstance = replayInstruction
                .createRequestReaderInstance(null);
        assertTrue(requestReaderInstance instanceof ReplayInstruction.ReplayRequestReader);
    }

//CreateResponseWriter tests

    @Test
    public void createReplayResponseWriter() {
        ReplayInstruction.ReplayResponseWriter responseWriterInstance = replayInstruction
                .createResponseWriterInstance(mock(Response.class));
        assertTrue(responseWriterInstance instanceof ReplayInstruction.ReplayResponseWriter);
    }

    @Test
    public void createResponseWriterInstanceWithNullResponse() {
        ReplayInstruction.ReplayResponseWriter responseWriterInstance = replayInstruction
                .createResponseWriterInstance(null);
        assertTrue(responseWriterInstance instanceof ReplayInstruction.ReplayResponseWriter);
    }

//ReplayRequestReader tests

    @Test
    public void getProcess(){
        fillRequestParameters();
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
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
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertEquals(START_ACTIVITY_VALUE, requestReader.getStartActivity());
    }

    @Test
    public void getPayload(){
        fillRequestParameters();
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertEquals(PAYLOAD_VALUE, requestReader.getPayload());
    }

    @Test
    public void isDeeptrace(){
        fillRequestParameters();
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertTrue(requestReader.isDeepTrace());
    }
    @Test
    public void isDeeptraceNotSet(){
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertFalse(requestReader.isDeepTrace());
    }

    @Test
    public void isTest(){
        fillRequestParameters();
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertTrue(requestReader.getTest());
    }

    @Test
    public void isTestNotSet(){
        ReplayInstruction.ReplayRequestReader requestReader = replayInstruction.getRequestReader();
        assertFalse(requestReader.getTest());
    }

//ReplayResponseWriter tests

    @Test
    public void setException(){
        ReplayInstruction.ReplayResponseWriter responseWriter = replayInstruction.getResponseWriter();
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
        ReplayInstruction.ReplayResponseWriter responseWriter = replayInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setMainLogId(MAIN_LOG_ID_VALUE);
        verify(mockedMap).put(MAIN_LOG_ID_KEY, MAIN_LOG_ID_VALUE);
    }
}