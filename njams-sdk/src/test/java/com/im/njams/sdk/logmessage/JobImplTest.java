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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import com.im.njams.sdk.NjamsConfiguration;
import com.im.njams.sdk.NjamsSender;
import com.im.njams.sdk.metadata.NjamsMetadataFactory;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.serializer.NjamsSerializers;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

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
     * This constructor calls super().
     */
    public JobImplTest() {
        super();
    }

    /**
     * This method cleans up the message and resets the Datamasking after each
     * method.
     */
    @After
    public void cleanUp() {
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
        JobImpl job = createDefaultJob();
        job.start();
        //This is set so the job can flush.
        job.setStatus(JobStatus.ERROR);
        //Create a group with four children
        GroupImpl group = (GroupImpl) job.createGroup(mockGroupModel("start")).build();
        Activity child1 = group.createChildActivity(mockModel("child1")).build();
        Activity child2 = group.createChildActivity(mockModel("child2")).build();
        Activity child3 = group.createChildActivity(mockModel("child3")).build();
        Activity child4 = group.createChildActivity(mockModel("child4")).build();

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

    private GroupModel mockGroupModel(String modelId) {
        GroupModel model = Mockito.mock(GroupModel.class);
        when(model.getId()).thenReturn(modelId);
        return model;
    }

    private ActivityModel mockModel(String modelId) {
        ActivityModel model = Mockito.mock(ActivityModel.class);
        when(model.getId()).thenReturn(modelId);
        return model;
    }

    /**
     * This method tests if the datamasking works for the in the SDK FAQ here
     * and only here described fields when the job is flushed.
     */
    @Test
    public void testDataMaskingAfterFlushing() {

        Path clientPath = new Path("SDK4", "TEST");

        final Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestReceiver.NAME);

        final SenderMock savingMock = new SenderMock();
        final NjamsState njamsState = new NjamsState();
        njamsState.start();

        NjamsProjectMessage projectMessage = new NjamsProjectMessage(
            NjamsMetadataFactory.createMetadataWith(clientPath, "bla", "bla2"), new NjamsFeatures(),
            new NjamsConfiguration(new Configuration(), null, null, null), new NjamsSender(savingMock), new NjamsState(),
            new NjamsJobs(njams.getNjamsMetadata(), njamsState, new NjamsFeatures(), null), settings);

        Path processPath = new Path("PROCESSES");
        projectMessage.createProcess(processPath);

        //add DataMasking
        DataMasking.addPattern(".*");
        //Create a job
        final ProcessModel process = projectMessage.getProcessModel(new Path(PROCESSPATHNAME));
        process.createActivity("id", "name", null);

        JobImpl job = (JobImpl) process.createJob();
        job.start();
        createFullyFilledActivity(job);
        fillJob(job);

        //This sets the job end time and flushes
        job.end();

        msg = (LogMessage) savingMock.getMessage();
        checkAllFields();
    }

    /**
     * This method fills some of the jobs fields.
     *
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
        job.addAttribute("SomeAttributeKey", "SomeAttributeValue");
    }

    /**
     * This method checks all fields of the logMessage if they are masked
     * correctly by concept. (See SDK FAQ)
     */
    private void checkAllFields() {
        //Check all fields of the job
        checkJobFields();
        //Check all fields of each activity of the job
        checkActivityFields();
    }

    /**
     * This method checks all fields of the logMessage that doesn't involve the
     * activities if they are masked correctly by concept. (See SDK FAQ)
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
        //Those won't be masked aswell because they were set directly by us, not by
        //the ExtractHandler [SDK-125]
        assertFalse(onlyAsterisksOrNull(msg.getCorrelationLogId()));
        assertFalse(onlyAsterisksOrNull(msg.getParentLogId()));
        assertFalse(onlyAsterisksOrNull(msg.getExternalLogId()));
        assertFalse(onlyAsterisksOrNull(msg.getObjectName()));
        assertFalse(onlyAsterisksOrNull(msg.getServiceName()));
        Map<String, String> attr = msg.getAttributes();
        attr.keySet().forEach(key -> assertFalse(onlyAsterisksOrNull(attr.get(key))));
    }

    /**
     * This method checks all fields of all activities in the logMessage, if
     * they are masked correctly by concept. (See SDK FAQ)
     */
    private void checkActivityFields() {
        List<com.faizsiegeln.njams.messageformat.v4.logmessage.Activity> activities = msg.getActivities();
        //Those shouldn't be masked
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getModelId())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getInstanceId())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getIteration().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getMaxIterations().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getParentInstanceId())));
        activities.forEach(activity -> activity.getPredecessors()
                .forEach(pred -> assertFalse(onlyAsterisksOrNull(pred.getFromInstanceId()))));
        activities.forEach(activity -> activity.getPredecessors()
                .forEach(pred -> assertFalse(onlyAsterisksOrNull(pred.getModelId()))));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSequence().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getExecution().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(((Long) activity.getDuration()).toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(((Long) activity.getCpuTime()).toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getActivityStatus().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getEventStatus().toString())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getName())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getSubProcessPath())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getSubProcess().getLogId())));

        //Those shouldn't be masked because they were set directly by us, not by the ExtractHandler [SDK-125]
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getEventMessage())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getEventCode())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getEventPayload())));
        activities.forEach(activity -> assertFalse(onlyAsterisksOrNull(activity.getStackTrace())));
        activities.stream().map((activity) -> activity.getAttributes()).forEachOrdered((actAttr) -> {
            actAttr.keySet().forEach(key -> assertFalse(onlyAsterisksOrNull(actAttr.get(key))));
        });

        //These should be masked, because they should always me masked and they can't be set by the ExtractHandler
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getInput())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getOutput())));
        activities.forEach(activity -> assertTrue(onlyAsterisksOrNull(activity.getStartData())));

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

    //This method tests the isFinished() method of the JobImpl.
    @Test
    public void testIsFinished() {

        JobImpl job = createDefaultJob();
        //Created
        assertEquals(JobStatus.CREATED, job.getStatus());
        assertFalse(job.isFinished());
        //Running
        job.start();
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertFalse(job.isFinished());
        //Success
        job.setStatus(JobStatus.SUCCESS);
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertFalse(job.isFinished());
        //Warning
        job.setStatus(JobStatus.WARNING);
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertFalse(job.isFinished());
        //Error
        job.setStatus(JobStatus.ERROR);
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertFalse(job.isFinished());
        //End
        job.end();
        assertEquals(JobStatus.ERROR, job.getStatus());
        assertTrue(job.isFinished());
        //Even with Running
        job.setStatus(JobStatus.RUNNING);
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertTrue(job.isFinished());
    }

    /**
     * This method tests if the startTime can be set explicitly.
     *
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testSetStartTimeBeforeStart() throws InterruptedException {
        JobImpl job = createDefaultJob();
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
     *
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testDoesntSetStartTimeBeforeStart() throws InterruptedException {
        JobImpl job = createDefaultJob();
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
    public void testSetStartTimeAfterStart() {
        Job job = createDefaultJob();

        assertNotNull(job.getStartTime());

        job.start();
        job.setStartTime(LocalDateTime.now());
    }

    /**
     * This method tests if the Status can be set back to created outside of
     * jobImpl.
     */
    @Test
    public void testSetStartTimeAfterStartWithSettingBackToCreated() {
        Job job = createDefaultJob();

        assertEquals(JobStatus.CREATED, job.getStatus());

        job.start();
        job.setStatus(JobStatus.CREATED);
        //The Status shouldn't have changed!
        assertNotEquals(JobStatus.CREATED, job.getStatus());
    }

    /**
     * This method tests if a job that isn't started (whose status is CREATED)
     * and is flushed flushes normally.
     */
    @Test
    public void testJobFlushWithoutStart() {
        JobImpl job = createDefaultJob();

        job.flush();
    }

    /**
     * This method tests if a job that isn't started (whose status is CREATED)
     * and is ended should be in the WARNING state.
     */
    @Test
    public void testJobEndWithoutStart() {
        JobImpl job = createDefaultJob();

        job.end();
        assertTrue(job.getStatus() == JobStatus.SUCCESS);
    }

    /**
     * This method tests if a job throws an exception if someone tries to add an
     * activity without starting the job first.
     */
    @Test(expected = NjamsSdkRuntimeException.class)
    public void testAddActivityWithoutStart() {
        JobImpl job = createDefaultJob();

        Activity act = mock(Activity.class);
        //This should throw an Exception
        job.addActivity(act);
    }

    /**
     * This method tests if a job adds an attribute correctly after the job has
     * been started.
     */
    @Test
    public void testAddAttributeWithStart() {
        JobImpl job = createDefaultJob();
        job.start();
        job.addAttribute("a", "b");
        assertEquals(job.getAttribute("a"), "b");
    }

    /**
     * This method tests if a job can add an attribute without starting the job
     * first.
     */
    @Test
    public void testAddAttributeWithoutStart() {
        JobImpl job = createDefaultJob();

        //This should work
        job.addAttribute("a", "b");
        assertEquals("b", job.getAttribute("a"));
    }

    @Test
    public void testAddAttributeFlushAndGetAttribute() {
        JobImpl job = createDefaultJob();

        job.addAttribute("a", "b");
        job.flush();
        assertFalse(job.getAttributes().isEmpty());
        assertEquals("b", job.getAttribute("a"));
    }

    @Test
    public void testSetStartActivity() {
        JobImpl job = createDefaultStartedJob();

        assertNull(job.getStartActivity());
        assertFalse(job.hasOrHadStartActivity);

        Activity startedActivity = getStartedActivityForJob(job);

        assertTrue(startedActivity.isStarter());
        assertEquals(startedActivity, job.getStartActivity());
        assertTrue(job.hasOrHadStartActivity);
    }

    @Test
    public void testSetStartActivityAndFlushIt() {
        JobImpl job = createDefaultStartedJob();

        Activity startedActivity = getStartedActivityForJob(job);

        startedActivity.end();

        assertEquals(ActivityStatus.SUCCESS, startedActivity.getActivityStatus());

        job.flush();

        assertNull(job.getStartActivity());
        assertTrue(job.hasOrHadStartActivity);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setMoreStartActivities() {
        JobImpl job = createDefaultStartedJob();

        Activity startedActivity = getStartedActivityForJob(job);

        getStartedActivityForJob(job);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setMoreStartActivitiesAfterFlushingTheFirstStartActivity() {
        JobImpl job = createDefaultStartedJob();

        Activity startedActivity = getStartedActivityForJob(job);

        startedActivity.end();

        job.flush();

        assertNull(job.getStartActivity());
        assertTrue(job.hasOrHadStartActivity);

        getStartedActivityForJob(job);
    }

    private class SenderMock implements Sender {
        private CommonMessage message;

        @Override
        public void init(Properties properties) {

        }

        @Override
        public void send(CommonMessage msg) {
            this.message = msg;
        }

        @Override
        public void close() {

        }

        @Override
        public String getName() {
            return null;
        }

        public CommonMessage getMessage(){
            return message;
        }
    }
}
