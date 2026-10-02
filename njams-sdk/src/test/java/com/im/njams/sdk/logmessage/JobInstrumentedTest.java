package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Tests the effect of a job's "instrumented" flag on whether its log message is sent: with log mode
 * {@link LogMode#EXCLUSIVE} a job that is not instrumented is suppressed, in any other mode the flag does not matter.
 */
public class JobInstrumentedTest {

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

    /** The log mode is snapshotted when the job is created, so it must be set before creating the job. */
    private JobImpl createStartedJob(LogMode logMode) {
        njams.configuration().get().setLogMode(logMode);
        JobImpl job = (JobImpl) process.createJob();
        job.start();
        return job;
    }

    private int endJobAndCountLogMessages(JobImpl job) {
        CountingSender sender = new CountingSender();
        TestSender.setSenderMock(sender);
        job.end(true);
        njams.stop(); // drains the asynchronous sender pool
        return sender.logMessages.get();
    }

    @Test
    public void exclusiveModeSuppressesJobThatIsNotInstrumented() {
        JobImpl job = createStartedJob(LogMode.EXCLUSIVE);
        job.activities().create(process.getActivity("act")).build();

        assertEquals(0, endJobAndCountLogMessages(job));
    }

    @Test
    public void completeModeSendsJobThatIsNotInstrumented() {
        JobImpl job = createStartedJob(LogMode.COMPLETE);
        job.activities().create(process.getActivity("act")).build();

        assertEquals(1, endJobAndCountLogMessages(job));
    }

    @Test
    public void exclusiveModeSendsJobWhoseActivityHasAnEventStatus() {
        JobImpl job = createStartedJob(LogMode.EXCLUSIVE);
        Activity activity = job.activities().create(process.getActivity("act")).build();
        activity.setEventStatus(EventStatus.SUCCESS);

        assertEquals(1, endJobAndCountLogMessages(job));
    }

    // ---- facet API: job.tracing().setInstrumented()/isInstrumented() ----

    @Test
    public void jobIsNotInstrumentedByDefault() {
        JobImpl job = createStartedJob(LogMode.COMPLETE);

        assertFalse(job.tracing().isInstrumented());
    }

    @Test
    public void exclusiveModeSendsJobInstrumentedViaFacet() {
        JobImpl job = createStartedJob(LogMode.EXCLUSIVE);
        job.activities().create(process.getActivity("act")).build();
        job.tracing().setInstrumented();

        assertEquals(1, endJobAndCountLogMessages(job));
    }

    @Test
    public void exclusiveModeSuppressesJobNotInstrumentedViaFacet() {
        JobImpl job = createStartedJob(LogMode.EXCLUSIVE);
        job.activities().create(process.getActivity("act")).build();
        assertFalse(job.tracing().isInstrumented());

        assertEquals(0, endJobAndCountLogMessages(job));
    }

    @Test
    public void sdkMarksJobInstrumentedWhenActivityGetsEventStatus() {
        JobImpl job = createStartedJob(LogMode.COMPLETE);
        Activity activity = job.activities().create(process.getActivity("act")).build();
        activity.setEventStatus(EventStatus.SUCCESS);

        assertTrue(job.tracing().isInstrumented());
    }

    @Test
    public void sdkMarksJobInstrumentedForNonBlankEventMessageButNotForBlankOne() {
        JobImpl job = createStartedJob(LogMode.COMPLETE);
        Activity activity = job.activities().create(process.getActivity("act")).build();
        activity.setEventMessage(" ");
        assertFalse(job.tracing().isInstrumented());

        activity.setEventMessage("something happened");
        assertTrue(job.tracing().isInstrumented());
    }

    private static final class CountingSender extends AbstractSender {
        final AtomicInteger logMessages = new AtomicInteger();

        @Override
        public String getName() {
            return "COUNTING";
        }

        @Override
        public void send(CommonMessage msg, String clientSessionId) {
            if (msg instanceof LogMessage) {
                logMessages.incrementAndGet();
            }
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
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
