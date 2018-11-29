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

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author krautenberg@integrationmatters.com
 */
public class JobImplIT {
    
    private static JobImpl job;
    
    @BeforeClass
    public static void configure(){
        Path clientPath = new Path("Test");
        
        Settings config = new Settings();
        
        Njams njams = new Njams(clientPath, "1.0.0", "sdk4", config);
        Path processPath = new Path("Test2");
        ProcessModel process = njams.createProcess(processPath);
        
        job = (JobImpl)process.createJob("myJob");
        //This is set so the job can flush.
        job.setStatus(JobStatus.ERROR);
    }
    
    /**
     * This method tests if all the activites that are not RUNNING are deleted from
     * the activitiesMap in the job AND in the Group childActivities map aswell.
     * This wasn't the case since [SDK-76] was fixed.
     */
    @Test
    public void testFlushGroupWithChildren(){
        //Create a group with four children
        GroupImpl group = (GroupImpl) job.createGroup("start").build();
        Activity child1 = group.createChildActivity("child1").build();
        Activity child2 = group.createChildActivity("child2").build();
        Activity child3 = group.createChildActivity("child3").build();
        Activity child4 = group.createChildActivity("child4").build();
        
        //This shouldn't remove any child, because they are all RUNNING       
        job.flush();
        //Neither in the JobImpl object
        Collection<Activity> jobActivities = job.getActivities();
        assertTrue(jobActivities.contains(child1));
        assertTrue(jobActivities.contains(child2));
        assertTrue(jobActivities.contains(child3));
        assertTrue(jobActivities.contains(child4));
        //Nor in the GroupImpl object
        Collection<Activity> childActivities = group.getChildActivities();
        assertTrue(childActivities.contains(child1));
        assertTrue(childActivities.contains(child2));
        assertTrue(childActivities.contains(child3));
        assertTrue(childActivities.contains(child4));
        
        //The ActivityStatuses are changed
        child1.setActivityStatus(ActivityStatus.SUCCESS);
        child2.setActivityStatus(ActivityStatus.WARNING);
        child3.setActivityStatus(ActivityStatus.ERROR);
        //This should remove child 1,2 and 3, but not 4 from the JobImpl object
        job.flush();     
        jobActivities = job.getActivities();
        assertFalse(jobActivities.contains(child1));
        assertFalse(jobActivities.contains(child2));
        assertFalse(jobActivities.contains(child3));
        assertTrue(jobActivities.contains(child4));
        //Aswell as from the GroupImpl object
        childActivities = group.getChildActivities();
        assertFalse(childActivities.contains(child1));
        assertFalse(childActivities.contains(child2));
        assertFalse(childActivities.contains(child3));
        assertTrue(childActivities.contains(child4));
    }
    
}
