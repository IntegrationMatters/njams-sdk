package com.im.njams.sdk;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.logmessage.Job;

/**
 * Unit tests for {@link NjamsJobs}: the running-job registry and the replay-marker bookkeeping.
 */
public class NjamsJobsTest {

    private LifecycleState lifecycle;
    private NjamsJobs jobs;

    @Before
    public void setUp() {
        lifecycle = new LifecycleState();
        jobs = new NjamsJobs(lifecycle);
    }

    private static Job job(String jobId, String logId) {
        Job job = mock(Job.class);
        when(job.getJobId()).thenReturn(jobId);
        when(job.getLogId()).thenReturn(logId);
        return job;
    }

    @Test
    public void addBeforeStartThrows() {
        try {
            jobs.add(job("j1", "l1"));
            fail("expected NjamsSdkRuntimeException when not started");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void addGetRemove() {
        lifecycle.setStarted(true);
        Job job = job("j1", "l1");
        jobs.add(job);

        assertSame(job, jobs.get("j1"));
        assertTrue(jobs.getAll().contains(job));

        jobs.remove("j1");
        assertNull(jobs.get("j1"));
    }

    @Test
    public void getUnknownReturnsNull() {
        assertNull(jobs.get("nope"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getAllIsUnmodifiable() {
        lifecycle.setStarted(true);
        jobs.add(job("j1", "l1"));
        jobs.getAll().clear();
    }

    @Test
    public void setReplayMarkerWithBlankLogIdIsNoOp() {
        // must not throw
        jobs.setReplayMarker("", true);
        jobs.setReplayMarker(null, true);
    }

    @Test
    public void setReplayMarkerForPresentJobSetsDeepTrace() {
        lifecycle.setStarted(true);
        Job job = job("j1", "l1");
        jobs.add(job);

        jobs.setReplayMarker("l1", true);

        verify(job).setDeepTrace(true);
    }

    @Test
    public void rememberedReplayMarkerIsAppliedWhenJobIsAddedLater() {
        lifecycle.setStarted(true);
        // marker set before the job exists
        jobs.setReplayMarker("l2", true);

        Job job = job("j2", "l2");
        jobs.add(job);

        // the remembered deep-trace marker is applied on add
        verify(job).setDeepTrace(true);
    }
}
