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
package com.im.njams.sdk.client;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.JobImpl;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This class tests the LogMessageFlushTask class
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.4
 */
public class LogMessageFlushTaskTest extends AbstractTest{

    public LogMessageFlushTaskTest(){
        super();       
    }
    
    /**
     * Test of start method, of class LogMessageFlushTask.
     */
    @Test
    public void testStop(){
        LogMessageFlushTask.start(njams);
        JobImpl job = createDefaultJob();
        //A job is in the njams instance.
        assertFalse(njams.getJobs().isEmpty());
        assertEquals(njams.getJobById(job.getLogId()), job);
        job.start();
        
        assertTrue(job.getActivities().isEmpty());
        Activity activity = createDefaultActivity(job);
        assertFalse(job.getActivities().isEmpty());
        //This activity will be flushed
        activity.setActivityStatus(ActivityStatus.SUCCESS);
        assertFalse(job.getActivities().isEmpty());
        
        //call stop for this njams instance
        LogMessageFlushTask.stop(njams);
        //The job shouldn't be completly flushed, just the finished activity.
        assertTrue(job.getActivities().isEmpty());
        assertFalse(njams.getJobs().isEmpty());
    }   
    
    
    /**
     * This test isn't useful anymore, because njams needs to be started before
     * it can add jobs.
    @Test
    public void testStopWithoutStart(){
        JobImpl job = createDefaultJob();
        //A job is in the njams instance.
        assertFalse(njams.getJobs().isEmpty());
        assertEquals(njams.getJobById(job.getLogId()), job);
        job.start();
        
        assertTrue(job.getActivities().isEmpty());
        Activity activity = createDefaultActivity(job);
        assertFalse(job.getActivities().isEmpty());
        //This activity will be flushed
        activity.setActivityStatus(ActivityStatus.SUCCESS);
        assertFalse(job.getActivities().isEmpty());
        
        //call stop for this njams instance, without starting it before!
        LogMessageFlushTask.stop(njams);
        //The job shouldn't be flushed at all
        assertFalse(job.getActivities().isEmpty());
        assertEquals(job.getActivityByModelId(ACTIVITYMODELID), activity);
        assertFalse(njams.getJobs().isEmpty());
    }
    * */
}
