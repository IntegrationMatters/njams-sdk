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

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.common.SubProcess;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.im.njams.sdk.AbstractTest;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

import java.time.LocalDateTime;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * This class tests some methods of the JobImpl.
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.4
 */
public class JobImplTest extends AbstractTest {

    //This is used for testDataMaskingAfterFlushing, for mockito.
    private LogMessage msg;

    /**
     * This method configures njams with default values.
     */
    @BeforeClass
    public static void configure() {
        configureNjams(new Settings());
    }
    
    /**
     * This method cleans up the message and resets the Datamasking after each 
     * method.
     */
    @After
    public void cleanUp(){
        msg = null;
        DataMasking.removePatterns();
    }

    /**
     * This method tests if all the activites that are not RUNNING are deleted
     * from the activitiesMap in the job AND in the Group childActivities map
     * aswell.
     */
    @Test
    public void testFlushGroupWithChildren() {
        ProcessModel process = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) process.createJob();
        job.start();
        //This is set so the job can flush.
        job.setStatus(JobStatus.ERROR);
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
    /**
     * This method tests if the datamasking works for the here
     * <a href="https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ"> SDK
     * FAQ</a>
     * and only here described fields when the job is flushed.
     */
    @Test
    public void testDataMaskingAfterFlushing() {

        Path clientPath = new Path("SDK4", "TEST");

        Njams mockedNjams = spy(new Njams(clientPath, "1.0.0", "sdk4", new Settings()));
        Path processPath = new Path("PROCESSES");
        mockedNjams.createProcess(processPath);
        //add DataMasking
        DataMasking.addPattern(".*");
        //Create a job
        ProcessModel process = mockedNjams.getProcessModel(new Path(PROCESSPATHNAME));
        process.createActivity("id", "name", null);
        JobImpl job = (JobImpl) process.createJob();

        //Inject or own sender.send() method to get the masked logmessage       
        NjamsSender sender = mock(NjamsSender.class);
        when(mockedNjams.getSender()).thenReturn(sender);
        doAnswer((Answer<Object>) (InvocationOnMock invocation) -> {
            msg = (LogMessage) invocation.getArguments()[0];
            return null;
        }).when(sender).send(any(CommonMessage.class));

        job.start();
        createFullyFilledActivity(job);
        fillJob(job);

        //This sets the job end time and flushes
        job.end();
        checkAllFields();

    }

    /**
     * This method creates an Activity and sets static data to all the fields
     * that are needed for the MessageFormat's Activity.
     * @param job the job on which the activity is created.
     */
    private void createFullyFilledActivity(JobImpl job) {
        ActivityImpl act = (ActivityImpl) job.createActivity("id").build();
        act.setInput("SomeInput");
        act.setOutput("SomeOutput");
        act.setMaxIterations(5L);
        act.setParentInstanceId("SomeParent");
        act.setEventStatus(2);
        act.setEventMessage("SomeEventMessage");
        act.setEventCode("SomeEventCode");
        act.setEventPayload("SomeEventPayload");
        act.setStackTrace("SomeStackTrace");
        act.setStartData("SomeStartData");
        act.addPredecessor("SomeKey", "SomeValue");
        //Set a SubProcess
        SubProcess subProcess = new SubProcess();
        subProcess.setLogId("SomeSubProcessLogId");
        subProcess.setName("SomeSubProcessName");
        subProcess.setPath("SomeSubProcessPath");
        act.setSubProcess(subProcess);
        //Set one Predecessor
        Predecessor pre = new Predecessor();
        pre.setFromInstanceId("SomePredecessorInstanceId");
        pre.setModelId("SomeModelId");
        act.addPredecessor("SomeModelId", "SomeTransitionModelId");
        //Set a set of attributes (with one attribute) and add one more with addAttribute().
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("SomeActAttribute1", "SomeValueForAttribute1");
        act.setAttributes(hashMap);
        act.addAttribute("SomeActAttribute2", "SomeValueForAttribute2");
    }

    /**
     * This method fills some of the jobs fields.
     * @param job the job whose fields are filled with static data.
     */
    private void fillJob(JobImpl job) {
        //Fill the job with everything that will be posted in the logmessage
        job.setParentLogId("SomeParentLogId");
        job.setExternalLogId("SomeExternalLogId");
        job.setBusinessService("SomeBusinessService");
        job.setBusinessObject("SomeBusinessObject");
        job.setBusinessStart(LocalDateTime.now());
        job.setBusinessEnd(LocalDateTime.now());
        job.addAtribute("SomeAttributeKey", "SomeAttributeValue");
    }

    /**
     * This method checks all fields of the logMessage if they are masked 
     * correctly by concept. (See
     * <a href="https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ"> SDK
     * FAQ</a>)
     */
    private void checkAllFields() {
        //Check all fields of the job
        checkJobFields();
        //Check all fields of each activity of the job
        checkActivityFields();
    }

    /**
     * This method checks all fields of the logMessage that doesn't involve the
     * activities if they are masked correctly by concept. (See
     * <a href="https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ"> SDK
     * FAQ</a>)
     */
    private void checkJobFields() {
        //Those shouldn't be masked
        assertFalse(onlyAsterisksOrNull(msg.getMessageVersion().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getMessageNo().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getSentAt().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getLogId()));
        assertFalse(onlyAsterisksOrNull(msg.getJobId()));
        assertFalse(onlyAsterisksOrNull(msg.getProcessName()));
        assertFalse(onlyAsterisksOrNull(msg.getMachineName()));
        assertFalse(onlyAsterisksOrNull(msg.getJobStart().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getJobEnd().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getBusinessStart().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getBusinessEnd().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getStatus().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getMaxSeverity().toString()));
        assertFalse(onlyAsterisksOrNull(msg.getTrace().toString()));
        //getPlugins.toString()?
        assertFalse(onlyAsterisksOrNull(msg.getPlugins().toString()));
        //Those should be masked
        assertTrue(onlyAsterisksOrNull(msg.getCorrelationLogId()));
        assertTrue(onlyAsterisksOrNull(msg.getParentLogId()));
        assertTrue(onlyAsterisksOrNull(msg.getExternalLogId()));
        assertTrue(onlyAsterisksOrNull(msg.getObjectName()));
        assertTrue(onlyAsterisksOrNull(msg.getServiceName()));
        Map<String, String> attr = msg.getAttributes();
        attr.keySet().forEach(key -> assertTrue(onlyAsterisksOrNull(attr.get(key))));
    }

    /**
     * This method checks all fields of all activities in the logMessage, if
     * they are masked correctly by concept. (See
     * <a href="https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ"> SDK
     * FAQ</a>)
     */
    private void checkActivityFields() {
        List<com.faizsiegeln.njams.messageformat.v4.logmessage.Activity> activities = msg.getActivities();
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getModelId())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getInstanceId())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getIteration().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getMaxIterations().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getParentInstanceId())));
        activities.forEach(activity -> activity.getPredecessors().forEach(pred -> assertFalse(onlyAsterisksOrNull(pred.getFromInstanceId()))));
        activities.forEach(activity -> activity.getPredecessors().forEach(pred -> assertFalse(onlyAsterisksOrNull(pred.getModelId()))));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSequence().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getExecution().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(((Long) activity.getDuration()).toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(((Long) activity.getCpuTime()).toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getActivityStatus().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getEventStatus().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getName())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getPath())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getLogId())));

        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getInput())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getOutput())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getEventMessage())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getEventCode())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getEventPayload())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getStackTrace())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getStartData())));
        activities.stream().map((activity) -> activity.getAttributes()).forEachOrdered((actAttr) -> {
            actAttr.keySet().forEach(key -> assertTrue(onlyAsterisksOrNull(actAttr.get(key))));
        });
    }

    /**
     * This method checks if the word is consists of '*' only, is null or is
     * neither.
     *
     * @param wordToCheck the word to check for '*' or null
     * @return true if wordToCheck consists of '*' only or is null, false if
     * otherwise
     */
    private boolean onlyAsterisksOrNull(String wordToCheck) {
        boolean toRet;
        if (!StringUtils.isBlank(wordToCheck)) {
            toRet = Pattern.matches("(\\*)*", wordToCheck);
        } else {
            toRet = true;
        }
        return toRet;
    }
    /**
     * This method tests if the startTime can be set explicitly.
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testSetStartTimeBeforeStart() throws InterruptedException{
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        Job job = processModel.createJob();
        assertNotNull(job.getStartTime());
        
        Thread.sleep(100L);
        LocalDateTime tempTime = DateTimeUtility.now();
        job.setStartTime(tempTime);
        assertEquals(tempTime, job.getStartTime());
        
        job.start();
        //StartTime is still tempTime, because it was set explicitly.
        assertEquals(tempTime, job.getStartTime()); 
    }
    
    /**
     * This method tests if the startTime will be set automatically to the 
     * starting point.
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testDoesntSetStartTimeBeforeStart() throws InterruptedException{
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        Job job = processModel.createJob();
        assertNotNull(job.getStartTime());
        
        Thread.sleep(100L);
        LocalDateTime tempTime = job.getStartTime();
        job.start();
        //StartTime is different to tempTime, because it hasn't been set explicitly.
        assertNotEquals(tempTime, job.getStartTime()); 
    }
    
    /**
     * This method tests if the startTime will be set automatically to the 
     * starting point and can be changed after start() has been called.
     */
    @Test
    public void testSetStartTimeAfterStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        Job job = processModel.createJob();
        
        assertNotNull(job.getStartTime());
        
        job.start();
        job.setStartTime(LocalDateTime.now());
    }
    
    /**
     * This method tests if the Status can be set back to created outside of jobImpl.
     */
    @Test
    public void testSetStartTimeAfterStartWithSettingBackToCreated(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        Job job = processModel.createJob();
        
        assertEquals(JobStatus.CREATED, job.getStatus());
        
        job.start();
        job.setStatus(JobStatus.CREATED);
        //The Status shouldn't have changed!
        assertNotEquals(JobStatus.CREATED, job.getStatus());
    }
    
    /**
     * This method tests if a job that isn't started (whose status is CREATED) and is flushed
     * flushes normally.
     */
    @Test
    public void testJobFlushWithoutStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) processModel.createJob();
        
        job.flush();
    }
    
    /**
     * This method tests if a job that isn't started (whose status is CREATED) and is ended
     * should be in the WARNING state.
     */
    @Test
    public void testJobEndWithoutStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) processModel.createJob();
        
        job.end();
        assertTrue(job.getStatus() == JobStatus.WARNING);
    }
    
    /**
     * This method tests if a job throws an exception if someone tries to 
     * add an activity without starting the job first.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testAddActivityWithoutStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) processModel.createJob();
        
        Activity act = mock(Activity.class);
        //This should throw an Exception
        job.addActivity(act);
    }
    
    /**
     * This method tests if a job throws an exception if someone tries to 
     * add an attribute without starting the job first.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testAddAtributeWithoutStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) processModel.createJob();
        
        //This should throw an Exception
        job.addAtribute("a", "b");
    }
    
    /**
     * This method tests if a job throws an exception if someone tries to 
     * add an attribute without starting the job first.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testSetAttributeWithoutStart(){
        ProcessModel processModel = njams.getProcessModel(new Path(PROCESSPATHNAME));
        JobImpl job = (JobImpl) processModel.createJob();
        
        //This should throw an Exception
        job.setAttribute("a", "b");
    }
}
