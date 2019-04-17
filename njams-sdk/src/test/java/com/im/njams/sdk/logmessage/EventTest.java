/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.logmessage;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.DateTimeUtility;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * This class tests the Event class
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
public class EventTest extends AbstractTest {

    private Activity act;
    private Event evt;

    @Before
    public void getNewActivity(){
        act = mock(Activity.class);
        evt = new Event(act);
    }

    @Test
    public void isExecutionSetOnInit(){
        verify(act).getExecution();
        verify(act).setExecution(anyObject());
    }

    @Test
    public void testGetStatus() {
        evt.getStatus();
        verify(act).getEventStatus();
    }

    @Test
    public void testSetStatus() {
        EventStatus status = EventStatus.SUCCESS;
        evt.setStatus(status);
        verify(act).setEventStatus(status.getValue());
    }

    @Test
    public void testGetCode() {
        evt.getCode();
        verify(act).getEventCode();
    }

    @Test
    public void testSetCode() {
        final String test = "test";
        evt.setCode(test);
        verify(act).setEventCode(test);
    }

    @Test
    public void testGetMessage() {
        evt.getMessage();
        verify(act).getEventMessage();
    }

    @Test
    public void testSetMessage() {
        final String test = "test";
        evt.setMessage(test);
        verify(act).setEventMessage(test);
    }

    @Test
    public void testGetPayload() {
        evt.getPayload();
        verify(act).getEventPayload();
    }

    @Test
    public void testSetPayload() {
        final String test = "test";
        evt.setPayload(test);
        verify(act).setEventPayload(test);
    }

    @Test
    public void testGetStacktrace() {
        evt.getStacktrace();
        verify(act).getStackTrace();
    }

    @Test
    public void testSetStacktrace() {
        final String test = "test";
        evt.setStacktrace(test);
        verify(act).setStackTrace(test);
    }

    @Test
    public void testGetExecutionTime() {
        evt.getExecutionTime();
        verify(act, times(2)).getExecution();
    }

    @Test
    public void testSetExecutionTime() {
        final LocalDateTime exec = DateTimeUtility.now();
        evt.setExecutionTime(exec);
        verify(act, times(2)).setExecution(exec);
    }

    @Test
    public void testCallMethodsInSequence(){
        EventStatus status = EventStatus.SUCCESS;
        final String test = "test";
        final LocalDateTime exec = DateTimeUtility.now();
        evt.setStatus(status).setExecutionTime(exec).setCode(test).setMessage(test).setPayload(test).setStacktrace(test);
        verify(act).setEventStatus(status.getValue());
        verify(act).setExecution(exec);
        verify(act).setEventCode(test);
        verify(act).setEventMessage(test);
        verify(act).setEventPayload(test);
        verify(act).setStackTrace(test);
    }
}