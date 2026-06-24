package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link JobTracing}: the deep-trace, traces and instrumentation flags of a job.
 */
public class JobTracingTest {

    private JobTracing tracing;

    @Before
    public void setUp() {
        tracing = new JobTracing();
    }

    @Test
    public void flagsDefaultToFalse() {
        assertFalse(tracing.isDeepTrace());
        assertFalse(tracing.isTraces());
        assertFalse(tracing.isInstrumented());
    }

    @Test
    public void deepTraceCanBeToggled() {
        tracing.setDeepTrace(true);
        assertTrue(tracing.isDeepTrace());
        tracing.setDeepTrace(false);
        assertFalse(tracing.isDeepTrace());
    }

    @Test
    public void tracesCanBeToggled() {
        tracing.setTraces(true);
        assertTrue(tracing.isTraces());
        tracing.setTraces(false);
        assertFalse(tracing.isTraces());
    }

    @Test
    public void instrumentedIsLatchedOnce() {
        tracing.setInstrumented();
        assertTrue(tracing.isInstrumented());
        // there is no way to clear it again
        tracing.setInstrumented();
        assertTrue(tracing.isInstrumented());
    }
}
