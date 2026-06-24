package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Unit tests for {@link JobRuntimeConfig}: the per-job snapshot of the server-driven runtime
 * configuration plus the tracepoint and activity-configuration lookups.
 */
public class JobRuntimeConfigTest extends AbstractTest {

    @Test
    public void realConfigurationProvidesDefaults() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        assertEquals(LogMode.COMPLETE, cfg.logMode);
        assertEquals(LogLevel.INFO, cfg.logLevel);
        assertFalse(cfg.exclude);
        assertTrue(cfg.recording);
        // a configuration is present and recording is on -> the recorded attribute is added
        assertTrue(cfg.addRecordedAttribute);
    }

    @Test
    public void nullConfigurationFallsBackToDefaults() {
        ProcessModel pm = mock(ProcessModel.class);
        Njams njamsMock = mock(Njams.class);
        when(pm.getNjams()).thenReturn(njamsMock);
        when(njamsMock.getConfiguration()).thenReturn(null);
        when(pm.getPath()).thenReturn(Path.resolve("PROCESSES"));

        JobRuntimeConfig cfg = new JobRuntimeConfig(pm);

        assertEquals(LogMode.COMPLETE, cfg.logMode);
        assertEquals(LogLevel.INFO, cfg.logLevel);
        assertFalse(cfg.exclude);
        assertTrue(cfg.recording);
        // without a configuration, the recorded attribute is never added
        assertFalse(cfg.addRecordedAttribute);
    }

    @Test
    public void inactiveTracepointForNull() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        assertFalse(cfg.isActiveTracepoint(null));
    }

    @Test
    public void activeTracepointInsideWindow() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        TracepointExt tp = new TracepointExt();
        tp.setStarttime(DateTimeUtility.now().minusHours(1));
        tp.setEndtime(DateTimeUtility.now().plusHours(1));
        tp.setIterations(0); // 0 -> never exceeded
        assertTrue(cfg.isActiveTracepoint(tp));
    }

    @Test
    public void inactiveTracepointBeforeStart() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        TracepointExt tp = new TracepointExt();
        tp.setStarttime(DateTimeUtility.now().plusHours(1));
        tp.setEndtime(DateTimeUtility.now().plusHours(2));
        assertFalse(cfg.isActiveTracepoint(tp));
    }

    @Test
    public void inactiveTracepointAfterEnd() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        TracepointExt tp = new TracepointExt();
        tp.setStarttime(DateTimeUtility.now().minusHours(2));
        tp.setEndtime(DateTimeUtility.now().minusHours(1));
        assertFalse(cfg.isActiveTracepoint(tp));
    }

    @Test
    public void inactiveTracepointWhenIterationsExceeded() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        TracepointExt tp = new TracepointExt();
        tp.setStarttime(DateTimeUtility.now().minusHours(1));
        tp.setEndtime(DateTimeUtility.now().plusHours(1));
        tp.setIterations(5);
        tp.setCurrentIterations(5);
        assertFalse(cfg.isActiveTracepoint(tp));
    }

    @Test
    public void activityConfigurationNullForNullModel() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        assertNull(cfg.getActivityConfiguration(null));
    }

    @Test
    public void activityConfigurationNullWhenModelHasNoProcessModel() {
        JobRuntimeConfig cfg = new JobRuntimeConfig(process);
        ActivityModel model = mock(ActivityModel.class);
        when(model.getProcessModel()).thenReturn(null);
        assertNull(cfg.getActivityConfiguration(model));
    }
}
