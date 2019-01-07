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

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This class tests some methods of the ActivityImpl class.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.4
 */
public class ActivityImplTest extends AbstractTest {

    /**
     * This method configures njams with default values.
     */
    @BeforeClass
    public static void configure() {
        configureNjams(new Settings());
    }

    /**
     * This method tests the setEventStatus(..) method.
     */
    @Test
    public void testSetEventStatus() {
        ProcessModel model = njams.getProcessModel(new Path(PROCESSPATHNAME));
        Job job = model.createJob();
        ActivityModel activityModel = model.createActivity("act", "Act", null);
        ActivityImpl act = (ActivityImpl) job.createActivity(activityModel).build();

        //Initial there is no EventStatus and the JobStatus is CREATED.
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.CREATED, job.getStatus());

        //Here an invalid Status is tested. After that it should stay the same
        //as before
        try {
            act.setEventStatus(Integer.MIN_VALUE);
            fail();
        } catch (NjamsSdkRuntimeException e) {
        }
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.CREATED, job.getStatus());

        //Here the EventStatus is set to INFO and the JobStatus hasn't changed,
        //because there is no corresponding JobStatus.
        act.setEventStatus(0);
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.CREATED, job.getStatus());
        
        //Here the EventStatus is set to SUCCESS and the JobStatus likewise.
        act.setEventStatus(1);
        assertTrue(1 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here the EventStatus is set to null, but the JobStatus is not affected.
        //The JobStatus is still SUCCESS.
        act.setEventStatus(null);
        assertEquals(null, act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here the EventStatus is set to INFO, but the JobStatus is not affected.
        //The JobStatus is still SUCCESS.
        act.setEventStatus(0);
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());

        //Here an invalid Status is tested again. After that the EventStatus
        //is still INFO and the JobStatus is still SUCCESS.
        try {
            act.setEventStatus(Integer.MIN_VALUE);
            fail();
        } catch (NjamsSdkRuntimeException e) {
        }
        assertTrue(0 == act.getEventStatus());
        assertEquals(JobStatus.SUCCESS, job.getStatus());
        
        //Here the Event Status is set to WARNING and the JobStatus likewise.
        act.setEventStatus(2);
        assertTrue(2 == act.getEventStatus());
        assertEquals(JobStatus.WARNING, job.getStatus());
        
        //Here the Event Status is set to ERROR and the JobStatus likewise.
        act.setEventStatus(3);
        assertTrue(3 == act.getEventStatus());
        assertEquals(JobStatus.ERROR, job.getStatus());
        
        //Here the Event Status is set back to WARNING and the JobStatus likewise.
        act.setEventStatus(2);
        assertTrue(2 == act.getEventStatus());
        assertEquals(JobStatus.WARNING, job.getStatus());
    }
}
