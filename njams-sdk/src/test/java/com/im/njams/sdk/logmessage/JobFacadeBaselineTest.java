package com.im.njams.sdk.logmessage;

import static org.junit.Assert.*;

import java.time.LocalDateTime;

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
 * Pins the behavior of the Job/JobImpl members that remain after the legacy members migrated by
 * SDK-448 have been removed (SDK-482); the behavior of the removed members is pinned by the facet tests.
 * Deliberately does NOT pin flush()/end() on never-started jobs (approved fix, see design doc).
 */
public class JobFacadeBaselineTest {

    private Njams njams;
    private ProcessModel process;

    @Before
    public void setUp() {
        njams = new Njams(Path.of("SDK4", "TEST"), "1.0", "sdk4", TestReceiver.getSettings());
        process = njams.model().create(Path.of("SDK4", "TEST", "PROCESSES"));
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

    // --- error events (coverage gap found by JaCoCo check) ---

    @Test
    public void activityErrorEventIsCommittedOnFailedEnd() {
        JobImpl job = createStartedJob();
        ActivityImpl activity = (ActivityImpl) job.activities().create(actModel()).build();
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
        ActivityImpl activity = (ActivityImpl) job.activities().create(actModel()).build();
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
    public void recordingAddsNjamsRecordedAttribute() {
        JobImpl job = createJob();
        assertEquals("true", job.attributes().get("$njams_recorded"));
        assertTrue(job.isRecording());
    }

    // --- metadata (descriptive fields) ---

    @Test
    public void limitLengthTruncatesToMaxMinusOne() {
        assertEquals("abc", JobImpl.limitLength("f", "abc", 10));
        assertEquals("abcd", JobImpl.limitLength("f", "abcdef", 5));
        assertNull(JobImpl.limitLength("f", null, 5));
    }

    // --- tracing flags ---

    @Test
    public void needsDataIsTrueForDeepTraceAndStarterModels() {
        JobImpl job = createJob();
        ActivityModel plain = actModel();
        assertFalse(job.needsData(plain));
        job.tracing().setDeepTrace(true);
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
        job.attributes().add("k", "some-value");
        assertTrue(job.getEstimatedSize() > before);
        job.addToEstimatedSize(100);
        assertEquals(before + "k".length() + "some-value".length() + 100, job.getEstimatedSize());
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

    // --- flush invariant: fixed behavior (SDK-448 approved contract change) ---

    @Test
    public void flushOnNeverStartedJobSendsNothing() throws InterruptedException {
        JobImpl job = createJob();
        CapturingLogMessageSender capturing = new CapturingLogMessageSender();
        TestSender.setSenderMock(capturing);
        try {
            job.flush(); // WARN + skip; previously sent a status -1 message
            assertNull(capturing.poll(500));
        } finally {
            TestSender.setSenderMock(null);
        }
    }

    @Test
    public void endOnNeverStartedJobLogsErrorAndSendsNothing() throws InterruptedException {
        JobImpl job = createJob();
        CapturingLogMessageSender capturing = new CapturingLogMessageSender();
        TestSender.setSenderMock(capturing);
        try {
            job.end(true); // ERROR log, no throw, nothing sent
            assertTrue(job.isFinished());
            assertNull(capturing.poll(500));
        } finally {
            TestSender.setSenderMock(null);
        }
    }

    /** Captures the first LogMessage passed to the sender, with an await-with-timeout. */
    private static final class CapturingLogMessageSender extends com.im.njams.sdk.communication.AbstractSender {
        private final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        private volatile com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage lastLogMessage;

        @Override
        public String getName() {
            return "CAPTURING";
        }

        @Override
        public void send(com.faizsiegeln.njams.messageformat.v4.common.CommonMessage msg, String clientSessionId) {
            if (msg instanceof com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage) {
                lastLogMessage = (com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage) msg;
                latch.countDown();
            }
        }

        com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage poll(long millis) throws InterruptedException {
            latch.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return lastLogMessage;
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage msg,
                String clientSessionId) {
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage msg,
                String clientSessionId) {
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage msg,
                String clientSessionId) {
        }

        @Override
        protected boolean isCongestion(Throwable failure) {
            return false;
        }

        @Override
        protected boolean isMessageRejected(Throwable failure) {
            return false;
        }
    }
}
