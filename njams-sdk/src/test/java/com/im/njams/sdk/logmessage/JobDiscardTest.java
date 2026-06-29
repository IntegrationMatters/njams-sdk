package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.TestReceiver;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Tests SDK-465: {@link Job#discard()} releases a started job from the SDK without emitting a
 * {@link LogMessage}, marks it finished, is idempotent, and logs a warning for the discarded job.
 */
public class JobDiscardTest {

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

    private JobImpl createStartedJobWithActivity() {
        JobImpl job = (JobImpl) process.createJob();
        job.start();
        job.activities().create(process.getActivity("act")).build();
        return job;
    }

    /**
     * Drains the asynchronous sender thread pool by stopping the client; after this returns every
     * message that was going to be sent has been delivered to the mock.
     */
    private void drainSender() {
        njams.stop();
    }

    @Test
    public void discardedStartedJobNeverSendsLogMessage() {
        JobImpl job = createStartedJobWithActivity();
        CountingSender sender = new CountingSender();
        TestSender.setSenderMock(sender);

        job.discard();
        drainSender(); // also exercises the final flush on stop: the discarded job must not be flushed

        assertEquals(0, sender.logMessages.get());
    }

    @Test
    public void normalEndStillSendsExactlyOneLogMessage() {
        JobImpl job = createStartedJobWithActivity();
        CountingSender sender = new CountingSender();
        TestSender.setSenderMock(sender);

        job.end(true);
        drainSender();

        assertEquals(1, sender.logMessages.get());
    }

    @Test
    public void discardRemovesJobFromRegistryAndMarksFinished() {
        JobImpl job = createStartedJobWithActivity();

        job.discard();

        assertNull(njams.jobs().get(job.getJobId()));
        assertFalse(njams.jobs().getAll().contains(job));
        assertTrue(job.isFinished());
    }

    @Test
    public void discardIsIdempotentWhenCalledTwice() {
        JobImpl job = createStartedJobWithActivity();
        CountingSender sender = new CountingSender();
        TestSender.setSenderMock(sender);

        job.discard();
        job.discard(); // must not throw

        drainSender();
        assertEquals(0, sender.logMessages.get());
    }

    @Test
    public void discardAfterEndIsNoOp() {
        JobImpl job = createStartedJobWithActivity();
        CountingSender sender = new CountingSender();
        TestSender.setSenderMock(sender);

        job.end(true);
        job.discard(); // already finished -> no-op, no throw

        drainSender();
        assertEquals(1, sender.logMessages.get());
    }

    @Test
    public void discardingStartedJobLogsWarningOnce() {
        JobImpl job = createStartedJobWithActivity();
        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(JobImpl.class);
        logger.addAppender(appender);
        try {
            job.discard();
            job.discard(); // idempotent: must not produce a second warning

            List<String> warnings = appender.warningsContaining(job.getLogId());
            assertEquals(1, warnings.size());
        } finally {
            logger.removeAppender(appender);
        }
    }

    /** Counts {@link LogMessage}s handed to the sender. */
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
    }

    /** Captures log4j WARN events for assertions. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected synchronized void append(LoggingEvent event) {
            events.add(event);
        }

        synchronized List<String> warningsContaining(String needle) {
            List<String> result = new ArrayList<>();
            for (LoggingEvent event : events) {
                if (event.getLevel() == Level.WARN && String.valueOf(event.getRenderedMessage()).contains(needle)) {
                    result.add(event.getRenderedMessage());
                }
            }
            return result;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
