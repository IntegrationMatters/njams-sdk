package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ProcessModel;

/**
 * A flushed job sends its {@link LogMessage} through the sender that was registered for its client at
 * {@link LogMessageFlushTask#start(Njams, NjamsSender)}.
 */
public class JobFlusherSenderTest extends AbstractTest {

    private Njams other;

    @After
    public void stopOther() {
        if (other != null && other.isStarted()) {
            other.stop();
        }
    }

    @Test
    public void flushedJobSendsItsLogMessageThroughTheRegisteredSender() {
        NjamsSender sender = mock(NjamsSender.class);
        LogMessageFlushTask.start(njams, sender);
        JobImpl job = createDefaultStartedJob();

        job.end(true);

        verify(sender).send(any(LogMessage.class), eq(njams.metadata().getClientSessionId()));
    }

    @Test
    public void jobEndedAfterStopSendsNothingAndLogsAWarning() {
        NjamsSender sender = mock(NjamsSender.class);
        LogMessageFlushTask.start(njams, sender);
        JobImpl job = createDefaultStartedJob();
        LogMessageFlushTask.stop(njams);
        // stop() has flushed the started job; only what happens afterwards is of interest
        clearInvocations(sender);
        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(JobFlusher.class);
        logger.addAppender(appender);
        try {
            job.end(true);
        } finally {
            logger.removeAppender(appender);
        }

        verify(sender, never()).send(any(LogMessage.class), anyString());
        assertEquals(1, appender.warnings.size());
        assertTrue(appender.warnings.get(0), appender.warnings.get(0).contains(job.getLogId()));
    }

    @Test
    public void stopFlushesTheJobsToTheSenderOfTheRemovedEntry() {
        NjamsSender sender = mock(NjamsSender.class);
        LogMessageFlushTask.start(njams, sender);
        JobImpl job = createDefaultStartedJob();
        createDefaultActivity(job).setActivityStatus(ActivityStatus.SUCCESS);

        LogMessageFlushTask.stop(njams);

        verify(sender, times(1)).send(any(LogMessage.class), eq(njams.metadata().getClientSessionId()));
    }

    @Test
    public void everyInstanceUsesItsOwnSender() {
        other = new Njams(Path.of("SDK4", "OTHER"), CLIENTVERSION, CATEGORY, TestSender.getSettings());
        ProcessModel otherProcess = other.model().create("PROCESSES");
        otherProcess.createActivity(ACTIVITYMODELID, "Act", null);
        other.start();
        NjamsSender sender = mock(NjamsSender.class);
        NjamsSender otherSender = mock(NjamsSender.class);
        LogMessageFlushTask.start(njams, sender);
        LogMessageFlushTask.start(other, otherSender);
        JobImpl job = createDefaultStartedJob();
        JobImpl otherJob = (JobImpl) otherProcess.createJob();
        otherJob.start();

        job.end(true);
        verify(sender, times(1)).send(any(LogMessage.class), eq(njams.metadata().getClientSessionId()));
        verify(otherSender, never()).send(any(LogMessage.class), anyString());

        otherJob.end(true);
        verify(otherSender, times(1)).send(any(LogMessage.class), eq(other.metadata().getClientSessionId()));
        verify(sender, times(1)).send(any(LogMessage.class), anyString());
    }

    /** Captures the rendered WARN messages. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<String> warnings = new ArrayList<>();

        @Override
        protected synchronized void append(LoggingEvent event) {
            if (event.getLevel() == Level.WARN) {
                warnings.add(String.valueOf(event.getRenderedMessage()));
            }
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
