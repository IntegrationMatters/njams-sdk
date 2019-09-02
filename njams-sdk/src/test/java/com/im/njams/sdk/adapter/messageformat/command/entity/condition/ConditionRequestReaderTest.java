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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstructionTest.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConditionRequestReaderTest {

    private static final String PROCESS_PATH_KEY = "processPath";
    private static final String PROCESS_PATH_VALUE = "processPathValue";

    private static final String ACTIVITY_ID_KEY = "activityId";
    private static final String ACTIVITY_ID_VALUE = "activityIdValue";

    //ReplayRequestReader tests
    private Request requestMock;

    private ConditionRequestReader requestReader;

    @Before
    public void initialize(){
        requestMock = mock(Request.class);
        requestReader = new ConditionRequestReader(requestMock);
    }

    @Test
    public void getProcess() {
        fillParameters();
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
        assertEquals(ACTIVITY_ID_VALUE, requestReader.getActivityId());
    }

    @Test
    public void getExtract() throws NjamsInstructionException {
        fillParameters();
        assertEquals(EXTRACT_VALUE, serialize(requestReader.getExtract()));
    }

    @Test
    public void getNullExtract() throws NjamsInstructionException {
        Map<String, String> params = new HashMap<>();
        when(requestMock.getParameters()).thenReturn(params);
        params.put(EXTRACT_KEY, "null");
        assertEquals("null", serialize(requestReader.getExtract()));
    }

    @Test
    public void getEngineWideRecording() {
        fillParameters();
        assertEquals(ENGINE_WIDE_RECORDING_VALUE, requestReader.getEngineWideRecording());
    }

    @Test
    public void getProcessRecording() {
        fillParameters();
        assertEquals(PROCESS_RECORDING_VALUE, requestReader.getProcessRecording());
    }

    @Test
    public void getLogLevel() throws NjamsInstructionException {
        fillParameters();
        assertEquals(LogLevel.valueOf(LOG_LEVEL_VALUE), requestReader.getLogLevel());
    }

    @Test
    public void getLogMode() throws NjamsInstructionException {
        fillParameters();
        assertEquals(LogMode.valueOf(LOG_MODE_VALUE), requestReader.getLogMode());
    }

    @Test
    public void getExcluded() {
        fillParameters();
        assertEquals(Boolean.parseBoolean(EXCLUDE_VALUE), requestReader.getExcluded());
    }

    @Test
    public void getEndTime() throws NjamsInstructionException {
        fillParameters();
        assertEquals(LocalDateTime.parse(END_TIME_VALUE, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                requestReader.getEndTime());
    }

    @Test
    public void getTracingEnabled() {
        fillParameters();
        assertEquals(Boolean.parseBoolean(ENABLE_TRACING_VALUE), requestReader.getTracingEnabled());
    }

    @Test
    public void getStartTime() throws NjamsInstructionException {
        fillParameters();
        assertEquals(LocalDateTime.parse(START_TIME_VALUE, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                requestReader.getStartTime());
    }

    @Test
    public void getIterations() throws NjamsInstructionException {
        fillParameters();
        assertEquals(new Integer(ITERATIONS_VALUE), requestReader.getIterations());
    }

    @Test
    public void getDeepTrace() {
        fillParameters();
        assertEquals(Boolean.parseBoolean(DEEP_TRACE_VALUE), requestReader.getDeepTrace());
    }

    @Test
    public void collectAllMissingParameters() {
        fillParameters();

        final String doesntExist = "bla";
        String[] parametersToCheck = new String[]{PROCESS_PATH_KEY, ACTIVITY_ID_KEY, doesntExist};
        List<String> missingParameters = requestReader.collectAllMissingParameters(parametersToCheck);

        assertTrue(missingParameters.contains(doesntExist));
        assertFalse(missingParameters.contains(PROCESS_PATH_KEY));
        assertFalse(missingParameters.contains(ACTIVITY_ID_KEY));
    }

    @Test
    public void collectAllMissingParametersWithoutFillingParameters() {
        final String doesntExist = "bla";
        String[] parametersToCheck = new String[]{PROCESS_PATH_KEY, ACTIVITY_ID_KEY, doesntExist};
        List<String> missingParameters = requestReader.collectAllMissingParameters(parametersToCheck);

        assertTrue(missingParameters.contains(doesntExist));
        assertTrue(missingParameters.contains(PROCESS_PATH_KEY));
        assertTrue(missingParameters.contains(ACTIVITY_ID_KEY));
    }

    @Test
    public void collectAllMissingParametersWithEmptyStringArray() {
        List<String> missingParameters = requestReader.collectAllMissingParameters(new String[0]);

        assertTrue(missingParameters.isEmpty());
    }

    @Test
    public void collectAllMissingParametersWithNullStringArray() {
        List<String> missingParameters = requestReader.collectAllMissingParameters(null);

        assertTrue(missingParameters.isEmpty());
    }

}