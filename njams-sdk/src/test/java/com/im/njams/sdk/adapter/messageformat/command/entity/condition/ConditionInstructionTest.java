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

package com.im.njams.sdk.adapter.messageformat.command.entity.condition;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class ConditionInstructionTest {

    public static final String PROCESS_PATH_KEY = "processPath";
    public static final String PROCESS_PATH_VALUE = "processPathValue";

    public static final String ACTIVITY_ID_KEY = "activityId";
    public static final String ACTIVITY_ID_VALUE = "activityIdValue";

    public static final String EXTRACT_KEY = "extract";
    public static final Extract EXTRACT = new Extract();
    public static final String EXTRACT_VALUE = serialize(EXTRACT);

    public static final String LOG_LEVEL_KEY = "logLevel";
    public static final LogLevel LOG_LEVEL = LogLevel.INFO;
    public static final String LOG_LEVEL_VALUE = LOG_LEVEL.name();

    public static final String LOG_MODE_KEY = "logMode";
    public static final LogMode LOG_MODE = LogMode.COMPLETE;
    public static final String LOG_MODE_VALUE = LOG_MODE.name();

    public static final String EXCLUDE_KEY = "exclude";
    public static final boolean EXCLUDE = true;
    public static final String EXCLUDE_VALUE = String.valueOf(EXCLUDE);

    public static final String START_TIME_KEY = "starttime";
    public static final LocalDateTime START_TIME = DateTimeUtility.now();
    public static final String START_TIME_VALUE = DateTimeUtility.toString(START_TIME);

    public static final String END_TIME_KEY = "endtime";
    public static final LocalDateTime END_TIME = DateTimeUtility.now().plusMinutes(15);
    public static final String END_TIME_VALUE = DateTimeUtility.toString(END_TIME);

    public static final String ITERATIONS_KEY = "iterations";
    public static final int ITERATIONS = 10;
    public static final String ITERATIONS_VALUE = String.valueOf(10);

    public static final String DEEP_TRACE_KEY = "deepTrace";
    public static final boolean DEEP_TRACE = true;
    public static final String DEEP_TRACE_VALUE = String.valueOf(DEEP_TRACE);

    public static final String ENGINE_WIDE_RECORDING_KEY = "EngineWideRecording";
    public static final boolean ENGINE_WIDE_RECORDING = false;
    public static final String ENGINE_WIDE_RECORDING_VALUE = String.valueOf(ENGINE_WIDE_RECORDING);

    public static final String PROCESS_RECORDING_KEY = "Record";
    public static final boolean PROCESS_RECORDING = true;
    public static final String PROCESS_RECORDING_VALUE = String.valueOf(PROCESS_RECORDING);

    public static final String ENABLE_TRACING_KEY = "enableTracing";
    public static final boolean ENABLE_TRACING = true;
    public static final String ENABLE_TRACING_VALUE = String.valueOf(ENABLE_TRACING);

    private static String serialize(Object o) {
        try {
            return JsonUtils.serialize(o);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Instruction instructionMock;

    private Request requestMock;

    private Response responseMock;

    private ConditionInstruction conditionInstruction;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
        requestMock = mock(Request.class);
        responseMock = mock(Response.class);
        fillRequestAndResponse(requestMock, responseMock);
        conditionInstruction = spy(new ConditionInstruction(instructionMock));
    }

    private void fillRequestAndResponse(Request request, Response response) {
        when(instructionMock.getRequest()).thenReturn(request);
        when(instructionMock.getResponse()).thenReturn(response);
    }

//ReplayRequestReader tests

    @Test
    public void getProcess() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(PROCESS_PATH_VALUE, requestReader.getProcessPath());
    }

    private void fillParameters() {
        Map<String, String> params = new HashMap<>();
        when(requestMock.getParameters()).thenReturn(params);
        params.put(PROCESS_PATH_KEY, PROCESS_PATH_VALUE);
        params.put(ACTIVITY_ID_KEY, ACTIVITY_ID_VALUE);
        params.put(EXTRACT_KEY, EXTRACT_VALUE);
        params.put(LOG_LEVEL_KEY, LOG_LEVEL_VALUE);
        params.put(LOG_MODE_KEY, LOG_MODE_VALUE);
        params.put(EXCLUDE_KEY, EXCLUDE_VALUE);
        params.put(START_TIME_KEY, START_TIME_VALUE);
        params.put(END_TIME_KEY, END_TIME_VALUE);
        params.put(ITERATIONS_KEY, ITERATIONS_VALUE);
        params.put(DEEP_TRACE_KEY, DEEP_TRACE_VALUE);
        params.put(ENGINE_WIDE_RECORDING_KEY, ENGINE_WIDE_RECORDING_VALUE);
        params.put(PROCESS_RECORDING_KEY, PROCESS_RECORDING_VALUE);
        params.put(ENABLE_TRACING_KEY, ENABLE_TRACING_VALUE);
    }

    @Test
    public void getActivity() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(ACTIVITY_ID_VALUE, requestReader.getActivityId());
    }

    @Test
    public void getExtract() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(EXTRACT_VALUE, serialize(requestReader.getExtract()));
    }

    @Test
    public void getNullExtract() throws NjamsInstructionException {
        Map<String, String> params = new HashMap<>();
        when(requestMock.getParameters()).thenReturn(params);
        params.put(EXTRACT_KEY, "null");
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals("null", serialize(requestReader.getExtract()));
    }

    @Test
    public void getEngineWideRecording() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(ENGINE_WIDE_RECORDING_VALUE, requestReader.getEngineWideRecording());
    }

    @Test
    public void getProcessRecording() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(PROCESS_RECORDING_VALUE, requestReader.getProcessRecording());
    }

    @Test
    public void getLogLevel() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(LogLevel.valueOf(LOG_LEVEL_VALUE), requestReader.getLogLevel());
    }

    @Test
    public void getLogMode() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(LogMode.valueOf(LOG_MODE_VALUE), requestReader.getLogMode());
    }

    @Test
    public void getExcluded() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(Boolean.parseBoolean(EXCLUDE_VALUE), requestReader.getExcluded());
    }

    @Test
    public void getEndTime() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(LocalDateTime.parse(END_TIME_VALUE, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                requestReader.getEndTime());
    }

    @Test
    public void getTracingEnabled() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(Boolean.parseBoolean(ENABLE_TRACING_VALUE), requestReader.getTracingEnabled());
    }

    @Test
    public void getStartTime() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(LocalDateTime.parse(START_TIME_VALUE, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                requestReader.getStartTime());
    }

    @Test
    public void getIterations() throws NjamsInstructionException {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(new Integer(ITERATIONS_VALUE), requestReader.getIterations());
    }

    @Test
    public void getDeepTrace() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();
        assertEquals(Boolean.parseBoolean(DEEP_TRACE_VALUE), requestReader.getDeepTrace());
    }

    @Test
    public void collectAllMissingParameters() {
        fillParameters();
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();

        final String doesntExist = "bla";
        String[] parametersToCheck = new String[]{PROCESS_PATH_KEY, ACTIVITY_ID_KEY, doesntExist};
        List<String> missingParameters = requestReader.collectAllMissingParameters(parametersToCheck);

        assertTrue(missingParameters.contains(doesntExist));
        assertFalse(missingParameters.contains(PROCESS_PATH_KEY));
        assertFalse(missingParameters.contains(ACTIVITY_ID_KEY));
    }

    @Test
    public void collectAllMissingParametersWithoutFillingParameters() {
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();

        final String doesntExist = "bla";
        String[] parametersToCheck = new String[]{PROCESS_PATH_KEY, ACTIVITY_ID_KEY, doesntExist};
        List<String> missingParameters = requestReader.collectAllMissingParameters(parametersToCheck);

        assertTrue(missingParameters.contains(doesntExist));
        assertTrue(missingParameters.contains(PROCESS_PATH_KEY));
        assertTrue(missingParameters.contains(ACTIVITY_ID_KEY));
    }

    @Test
    public void collectAllMissingParametersWithEmptyStringArray() {
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();

        List<String> missingParameters = requestReader.collectAllMissingParameters(new String[0]);

        assertTrue(missingParameters.isEmpty());
    }

    @Test
    public void collectAllMissingParametersWithNullStringArray() {
        ConditionRequestReader requestReader = conditionInstruction.getRequestReader();

        List<String> missingParameters = requestReader.collectAllMissingParameters(null);

        assertTrue(missingParameters.isEmpty());
    }

//ReplayResponseWriter tests

    @Test
    public void setExtract() throws NjamsInstructionException {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setExtract(EXTRACT);
        verify(mockedMap).put(EXTRACT_KEY, EXTRACT_VALUE);
    }

    @Test
    public void setNullExtract() throws NjamsInstructionException {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
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
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogMode(LOG_MODE);
        verify(mockedMap).put(LOG_MODE_KEY, LOG_MODE_VALUE);
    }

    @Test
    public void setNullLogMode() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogMode(null);
        verify(mockedMap).put(LOG_MODE_KEY, null);
    }

    @Test
    public void setLogLevel() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogLevel(LOG_LEVEL);
        verify(mockedMap).put(LOG_LEVEL_KEY, LOG_LEVEL_VALUE);
    }

    @Test
    public void setNullLogLevel() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setLogLevel(null);
        verify(mockedMap).put(LOG_LEVEL_KEY, null);
    }

    @Test
    public void setExcluded() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setExcluded(EXCLUDE);
        verify(mockedMap).put(EXCLUDE_KEY, EXCLUDE_VALUE);
    }

    @Test
    public void setStartTime() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setStartTime(START_TIME);
        verify(mockedMap).put(START_TIME_KEY, START_TIME_VALUE);
    }

    @Test
    public void setNullStartTime() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setStartTime(null);
        verify(mockedMap).put(START_TIME_KEY, null);
    }

    @Test
    public void setEndTime() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setEndTime(END_TIME);
        verify(mockedMap).put(END_TIME_KEY, END_TIME_VALUE);
    }

    @Test
    public void setNullEndTime() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setEndTime(null);
        verify(mockedMap).put(END_TIME_KEY, null);
    }

    @Test
    public void setIterations() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setIterations(ITERATIONS);
        verify(mockedMap).put(ITERATIONS_KEY, ITERATIONS_VALUE);
    }

    @Test
    public void setDeepTrace() {
        ConditionResponseWriter responseWriter = conditionInstruction.getResponseWriter();
        Map<String, String> mockedMap = setMockedMap();
        responseWriter.setDeepTrace(DEEP_TRACE);
        verify(mockedMap).put(DEEP_TRACE_KEY, DEEP_TRACE_VALUE);
    }

}