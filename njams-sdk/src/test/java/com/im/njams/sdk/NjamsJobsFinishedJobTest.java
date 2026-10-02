package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.logmessage.JobImpl;

/**
 * Tests for the replay-marker handling of {@link NjamsJobs} on real jobs, in particular on jobs that have already
 * ended and therefore reject changes through the job facets.
 */
public class NjamsJobsFinishedJobTest extends AbstractTest {

    private NjamsJobs jobs;

    @Before
    public void setUp() {
        LifecycleState lifecycle = new LifecycleState();
        lifecycle.setStarted(true);
        jobs = new NjamsJobs(lifecycle);
    }

    @Test
    public void setReplayMarkerMarksRunningJob() {
        JobImpl job = createDefaultStartedJob();
        jobs.add(job);

        jobs.setReplayMarker(job.getLogId(), true);

        assertEquals("true", job.attributes().get(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE));
        assertTrue(job.tracing().isDeepTrace());
    }

    @Test
    public void setReplayMarkerOnFinishedJobDoesNotThrow() {
        JobImpl job = createDefaultStartedJob();
        jobs.add(job);
        job.end();

        jobs.setReplayMarker(job.getLogId(), true);

        assertFalse(job.attributes().has(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE));
    }

    @Test
    public void addWithRememberedReplayMarkerOnFinishedJobDoesNotThrow() {
        JobImpl job = createDefaultStartedJob();
        job.end();
        jobs.setReplayMarker(job.getLogId(), true);

        jobs.add(job);

        assertFalse(job.attributes().has(ReplayHandler.NJAMS_REPLAYED_ATTRIBUTE));
    }
}
