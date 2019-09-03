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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsReplayRequestReaderTest {

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

    private Request requestMock;

    private Map<String, String> parameters;

    private NjamsReplayRequestReader replayRequestReader;

    @Before
    public void initialize() {
        requestMock = mock(Request.class);
        parameters = new HashMap<>();
        when(requestMock.getParameters()).thenReturn(parameters);
        replayRequestReader = spy(new NjamsReplayRequestReader(requestMock));
    }

//GetProcess tests

    @Test
    public void getNullIfProcessIsNotSet() {
        assertNull(replayRequestReader.getProcess());
    }

    @Test
    public void getProcess() {
        fillRequestParameters();
        assertEquals(PROCESS_VALUE, replayRequestReader.getProcess());
    }

    private void fillRequestParameters() {
        parameters.put(PROCESS_KEY, PROCESS_VALUE);
        parameters.put(START_ACTIVITY_KEY, START_ACTIVITY_VALUE);
        parameters.put(PAYLOAD_KEY, PAYLOAD_VALUE);
        parameters.put(DEEPTRACE_KEY, DEEPTRACE_VALUE);
        parameters.put(TEST_KEY, TEST_VALUE);
    }

//GetStartActivity tests

    @Test
    public void getNullIfStartActivityIsNotSet() {
        assertNull(replayRequestReader.getStartActivity());
    }

    @Test
    public void getStartActivity() {
        fillRequestParameters();
        assertEquals(START_ACTIVITY_VALUE, replayRequestReader.getStartActivity());
    }

//GetPayload tests

    @Test
    public void getNullIfPayloadIsNotSet() {
        assertNull(replayRequestReader.getPayload());
    }

    @Test
    public void getPayload() {
        fillRequestParameters();
        assertEquals(PAYLOAD_VALUE, replayRequestReader.getPayload());
    }

//isDeeptrace tests

    @Test
    public void getFalseIfDeepTraceIsNotSet() {
        assertFalse(replayRequestReader.isDeepTrace());
    }

    @Test
    public void getFalseIfDeepTraceIsNotBooleanString() {
        parameters.put(DEEPTRACE_KEY, "NotBoolean");
        assertFalse(replayRequestReader.isDeepTrace());
    }

    @Test
    public void getDeepTrace() {
        fillRequestParameters();
        assertTrue(replayRequestReader.isDeepTrace());
    }

//isTest tests

    @Test
    public void getFalseIfTestIsNotSet() {
        assertFalse(replayRequestReader.isTest());
    }

    @Test
    public void getFalseIfTestIsNotBooleanString() {
        parameters.put(DEEPTRACE_KEY, "NotBoolean");
        assertFalse(replayRequestReader.isTest());
    }

    @Test
    public void getIsTest() {
        fillRequestParameters();
        assertTrue(replayRequestReader.isTest());
    }
}