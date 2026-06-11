package com.im.njams.sdk.logmessage;

import static org.junit.Assert.*;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Pins the current behavior of all public Job/JobImpl methods migrated by SDK-448,
 * BEFORE the refactoring. Must pass unchanged against the refactored class.
 * Deliberately does NOT pin flush()/end() on never-started jobs (approved fix, see design doc).
 */
public class JobFacadeBaselineTest {

    private Njams njams;
    private ProcessModel process;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "1.0", "sdk4", TestReceiver.getSettings());
        process = njams.processes().create(Path.of("SDK4", "TEST", "PROCESSES"));
        process.createActivity("act", "Act", null);
        njams.start();
    }

    @After
    public void tearDown() {
        if (njams.isStarted()) {
            njams.stop();
        }
        TestSender.setSenderMock(null);
    }

    private JobImpl createJob() {
        return (JobImpl) process.createJob();
    }

    private JobImpl createStartedJob() {
        JobImpl job = createJob();
        job.start();
        return job;
    }

    private ActivityModel actModel() {
        return process.getActivity("act");
    }

    // --- lifecycle / status ---

    @Test
    public void newJobIsCreatedNotStartedNotFinished() {
        JobImpl job = createJob();
        assertEquals(JobStatus.CREATED, job.getStatus());
        assertFalse(job.hasStarted());
        assertFalse(job.isFinished());
    }

    @Test
    public void startSetsRunningAndStartTime() {
        JobImpl job = createJob();
        job.start();
        assertTrue(job.hasStarted());
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertNotNull(job.getStartTime());
    }

    @Test
    public void explicitStartTimeSurvivesStart() {
        JobImpl job = createJob();
        LocalDateTime explicit = DateTimeUtility.now().minusDays(1);
        job.setStartTime(explicit);
        job.start();
        assertEquals(explicit, job.getStartTime());
    }

    @Test
    public void setStartTimeNullIsIgnoredWithWarning() {
        JobImpl job = createJob();
        LocalDateTime before = job.getStartTime();
        job.setStartTime(null); // must NOT throw
        assertEquals(before, job.getStartTime());
    }

    @Test
    public void setStatusBeforeStartOnlyWarns() {
        JobImpl job = createJob();
        job.setStatus(JobStatus.ERROR); // pinned lenient behavior: WARN, no throw, no change
        assertEquals(JobStatus.CREATED, job.getStatus());
    }

    @Test
    public void setStatusNullOrCreatedIsIgnored() {
        JobImpl job = createStartedJob();
        job.setStatus(null);
        job.setStatus(JobStatus.CREATED);
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    public void maxSeverityEscalatesButNeverDecreases() {
        JobImpl job = createStartedJob();
        job.setStatus(JobStatus.ERROR);
        job.setStatus(JobStatus.SUCCESS);
        assertEquals(JobStatus.ERROR, job.getMaxSeverity());
    }

    @Test
    public void endTrueWithoutStatusYieldsSuccess() {
        JobImpl job = createStartedJob();
        job.end(true);
        assertTrue(job.isFinished());
        assertEquals(JobStatus.SUCCESS, job.getStatus());
        assertNotNull(job.getEndTime());
    }

    @Test
    public void endFalseYieldsError() {
        JobImpl job = createStartedJob();
        job.end(false);
        assertEquals(JobStatus.ERROR, job.getStatus());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void endTwiceThrows() {
        JobImpl job = createStartedJob();
        job.end(true);
        job.end(true);
    }

    @Test
    public void endRemovesJobFromRegistry() {
        JobImpl job = createStartedJob();
        String jobId = job.getJobId();
        job.end(true);
        assertNull(njams.jobs().get(jobId));
    }

    @Test
    public void deprecatedEndDelegatesToEndTrue() {
        JobImpl job = createStartedJob();
        job.end();
        assertEquals(JobStatus.SUCCESS, job.getStatus());
    }

    // --- activities ---

    @Test(expected = NjamsSdkRuntimeException.class)
    public void addActivityBeforeStartThrows() {
        JobImpl job = createJob();
        job.createActivity(actModel()).build(); // build() -> addActivity -> throws
    }

    @Test
    public void activityLifecycleAfterStart() {
        JobImpl job = createStartedJob();
        Activity activity = job.createActivity(actModel()).build();
        assertSame(activity, job.getActivityByInstanceId(activity.getInstanceId()));
        assertSame(activity, job.getActivityByModelId("act"));
        assertSame(activity, job.getRunningActivityByModelId("act"));
        assertNull(job.getCompletedActivityByModelId("act"));
        activity.end();
        assertSame(activity, job.getCompletedActivityByModelId("act"));
        assertNull(job.getRunningActivityByModelId("act"));
        assertEquals(1, job.getActivities().size());
    }

    @Test
    public void getActivitiesReturnsDetachedCopy() {
        JobImpl job = createStartedJob();
        job.createActivity(actModel()).build();
        job.getActivities().clear();
        assertEquals(1, job.getActivities().size());
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void secondStartActivityThrows() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starter", "Starter", null);
        starter.setStarter(true);
        job.createActivity(starter).setStarter().build();
        job.createActivity(starter).setStarter().build();
    }

    @Test
    public void startActivityIsTracked() {
        JobImpl job = createStartedJob();
        ActivityModel starter = process.createActivity("starter2", "Starter2", null);
        starter.setStarter(true);
        Activity activity = job.createActivity(starter).setStarter().build();
        assertSame(activity, job.getStartActivity());
    }

    @Test
    public void createSubProcessReturnsBuilder() {
        JobImpl job = createStartedJob();
        com.im.njams.sdk.model.SubProcessActivityModel subModel =
            process.createSubProcess("sub", "Sub", null);
        Activity activity = job.createSubProcess(subModel).build();
        assertNotNull(activity);
        assertSame(activity, job.getActivityByModelId("sub"));
    }

    // --- error events (coverage gap found by JaCoCo check) ---

    @Test
    public void activityErrorEventIsCommittedOnFailedEnd() {
        JobImpl job = createStartedJob();
        ActivityImpl activity = (ActivityImpl) job.createActivity(actModel()).build();
        ErrorEvent error = new ErrorEvent().setCode("E1").setMessage("boom");
        job.setActivityErrorEvent(activity, error);
        job.end(false);
        // default LOG_ALL_ERRORS=false: error is stored and committed at failed end
        assertEquals(Integer.valueOf(EventStatus.ERROR.getValue()), activity.getEventStatus());
        assertEquals("E1", activity.getEventCode());
        assertEquals("boom", activity.getEventMessage());
    }

    @Test
    public void activityErrorEventIsDiscardedOnSuccessfulEnd() {
        JobImpl job = createStartedJob();
        ActivityImpl activity = (ActivityImpl) job.createActivity(actModel()).build();
        job.setActivityErrorEvent(activity, new ErrorEvent().setCode("E1").setMessage("boom"));
        job.end(true);
        assertNull(activity.getEventCode());
    }

    @Test
    public void addPluginDataItemIsAccepted() {
        JobImpl job = createStartedJob();
        com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem item =
            new com.faizsiegeln.njams.messageformat.v4.logmessage.PluginDataItem();
        job.addPluginDataItem(item); // must NOT throw; sent and cleared with the next flush
        job.end(true);
    }

    // --- attributes ---

    @Test
    public void attributesArePutAndQueried() {
        JobImpl job = createStartedJob();
        job.addAttribute("k", "v");
        assertEquals("v", job.getAttribute("k"));
        assertTrue(job.hasAttribute("k"));
        assertFalse(job.hasAttribute("missing"));
        Map<String, String> all = job.getAttributes();
        assertEquals("v", all.get("k"));
        all.clear(); // detached copy
        assertTrue(job.hasAttribute("k"));
    }

    @Test
    public void nullAttributeValueIsIgnored() {
        JobImpl job = createStartedJob();
        job.addAttribute("k", null); // must NOT throw
        assertFalse(job.hasAttribute("k"));
    }

    @Test
    public void recordingAddsNjamsRecordedAttribute() {
        JobImpl job = createJob();
        assertEquals("true", job.getAttribute("$njams_recorded"));
        assertTrue(job.isRecording());
    }

    // --- metadata (descriptive fields) ---

    @Test
    public void correlationLogIdDefaultsToLogIdAndIsSettable() {
        JobImpl job = createJob();
        assertEquals(job.getLogId(), job.getCorrelationLogId());
        job.setCorrelationLogId("corr-1");
        assertEquals("corr-1", job.getCorrelationLogId());
    }

    @Test
    public void parentAndExternalLogIdAreSettable() {
        JobImpl job = createJob();
        job.setParentLogId("p-1");
        job.setExternalLogId("e-1");
        assertEquals("p-1", job.getParentLogId());
        assertEquals("e-1", job.getExternalLogId());
    }

    @Test
    public void businessServiceAndObjectAcceptStringAndPath() {
        JobImpl job = createJob();
        job.setBusinessService("svc");
        job.setBusinessObject(Path.of("obj"));
        assertNotNull(job.getBusinessService());
        assertNotNull(job.getBusinessObject());
        job.setBusinessService((Path) null); // null Path ignored, must NOT throw
    }

    @Test
    public void businessStartAndEndAreSettable() {
        JobImpl job = createJob();
        LocalDateTime t = DateTimeUtility.now();
        job.setBusinessStart(t);
        job.setBusinessEnd(t);
        assertEquals(t, job.getBusinessStart());
        assertEquals(t, job.getBusinessEnd());
    }

    @Test
    public void overlongFieldValueIsTruncated() {
        JobImpl job = createJob();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < JobImpl.MAX_VALUE_LIMIT + 100; i++) {
            sb.append('x');
        }
        job.setParentLogId(sb.toString());
        assertEquals(JobImpl.MAX_VALUE_LIMIT - 1, job.getParentLogId().length());
    }

    @Test
    public void limitLengthTruncatesToMaxMinusOne() {
        assertEquals("abc", JobImpl.limitLength("f", "abc", 10));
        assertEquals("abcd", JobImpl.limitLength("f", "abcdef", 5));
        assertNull(JobImpl.limitLength("f", null, 5));
    }

    // --- properties ---

    @Test
    public void propertiesRoundTrip() {
        JobImpl job = createJob();
        assertFalse(job.hasProperty("p"));
        job.setProperty("p", 42);
        assertTrue(job.hasProperty("p"));
        assertEquals(42, job.getProperty("p"));
        assertEquals(42, job.removeProperty("p"));
        assertNull(job.getProperty("p"));
        assertNull(job.removeProperty("p"));
    }

    // --- tracing flags ---

    @Test
    public void deepTraceAndTracesFlags() {
        JobImpl job = createJob();
        assertFalse(job.isDeepTrace());
        job.setDeepTrace(true);
        assertTrue(job.isDeepTrace());
        assertFalse(job.isTraces());
        job.setTraces(true);
        assertTrue(job.isTraces());
    }

    @Test
    public void needsDataIsTrueForDeepTraceAndStarterModels() {
        JobImpl job = createJob();
        ActivityModel plain = actModel();
        assertFalse(job.needsData(plain));
        job.setDeepTrace(true);
        assertTrue(job.needsData(plain));
    }

    // --- flushing infrastructure (kept behavior only) ---

    @Test
    public void timerFlushBeforeStartIsSkippedSilently() {
        JobImpl job = createJob();
        job.timerFlush(DateTimeUtility.now().plusDays(1), 0); // must NOT throw, must not flush
        assertFalse(job.hasStarted());
    }

    @Test
    public void estimatedSizeGrowsWithContent() {
        JobImpl job = createStartedJob();
        long before = job.getEstimatedSize();
        job.addAttribute("k", "some-value");
        assertTrue(job.getEstimatedSize() > before);
        job.addToEstimatedSize(100);
        assertEquals(before + "k".length() + "some-value".length() + 100, job.getEstimatedSize());
    }

    @Test
    public void lastFlushIsInitialized() {
        assertNotNull(createJob().getLastFlush());
    }

    @Test
    public void getNjamsReturnsOwner() {
        assertSame(njams, createJob().getNjams());
    }

    // --- payload limiting (no limit configured in test settings) ---

    @Test
    public void noPayloadLimitConfiguredMeansPassThrough() {
        JobImpl job = createJob();
        assertEquals(0, job.getSerializeSizeHint());
        assertEquals("payload", job.limitPayload("payload"));
        assertNull(job.limitPayload(null));
    }

    @Test
    public void toStringContainsLogAndJobId() {
        JobImpl job = createJob();
        assertTrue(job.toString().contains(job.getLogId()));
        assertTrue(job.toString().contains(job.getJobId()));
    }
}
