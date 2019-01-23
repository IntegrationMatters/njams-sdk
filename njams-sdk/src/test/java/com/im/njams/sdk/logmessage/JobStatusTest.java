/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This class tests the JobStatus Enum.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.4
 */
public class JobStatusTest {

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testByValueWithMinValue() {
        JobStatus.byValue(Integer.MIN_VALUE);
    }
    
    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testByValueBelowNormal() {
        EventStatus.byValue(-2);
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueCreated() {
        assertEquals(JobStatus.CREATED, JobStatus.byValue(-1));
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueRunning() {
        assertEquals(JobStatus.RUNNING, JobStatus.byValue(0));
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueSuccess() {
        assertEquals(JobStatus.SUCCESS, JobStatus.byValue(1));
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueWarning() {
        assertEquals(JobStatus.WARNING, JobStatus.byValue(2));
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueError() {
        assertEquals(JobStatus.ERROR, JobStatus.byValue(3));
    }
    
    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testByValueAboveNormal() {
        EventStatus.byValue(4);
    }

    /**
     * Test of byValue method, of class JobStatus.
     */
    @Test
    public void testByValueNull() {
        assertNull(JobStatus.byValue(null));
    }

}
