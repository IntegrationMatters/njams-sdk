package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class TruncatingTest {

    private final Random random = new Random();
    private ProcessModel processModel = null;
    private Settings settings;

    @Before
    public void setup() {
        processModel = mock(ProcessModel.class);
        settings = new Settings();
    }

    private JobImpl buildJobImpl(int limit, boolean onSuccess) {
        settings.put(JobImpl.TRUNCATE_LIMIT, String.valueOf(limit));
        settings.put(JobImpl.TRUNCATE_ON_SUCCESS, String.valueOf(onSuccess));

        return new JobImpl(processModel, "4711", "4812", null, null, null, null, settings, null);
    }

    private Activity activity() {
        Activity a = mock(Activity.class);
        when(a.getInstanceId()).thenReturn(String.valueOf(random.nextInt()));
        when(a.getEventStatus()).thenReturn(null);
        return a;
    }

    private Activity event() {
        Activity a = activity();
        when(a.getEventCode()).thenReturn("event");
        return a;
    }

    @Test
    public void testLimit10() {
        JobImpl job = buildJobImpl(10, false);
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));

        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));

    }

    @Test
    public void testLimit10_2() {
        JobImpl job = buildJobImpl(10, false);
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));

        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));

        // no more activities
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));

        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));

        // no more events
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));

        assertFalse(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));
    }

    @Test
    public void testAllEvents() {
        JobImpl job = buildJobImpl(5, false);
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));

        assertFalse(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(activity(), false));
    }

    @Test
    public void testDisabled() {
        JobImpl job = buildJobImpl(0, false);
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
    }

    @Test
    public void testOnSuccess() {
        JobImpl job = buildJobImpl(10, true);
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));

        assertFalse(job.checkTruncating(activity(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        assertTrue(job.checkTruncating(event(), true));
        // limit 10
        assertFalse(job.checkTruncating(event(), true));
    }

    @Test
    public void testOnSuccess2() {
        JobImpl job = buildJobImpl(10, true);
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertTrue(job.checkTruncating(activity(), false));
        assertFalse(job.checkTruncating(activity(), false));

        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertTrue(job.checkTruncating(event(), false));
        assertFalse(job.checkTruncating(event(), false));

        assertFalse(job.checkTruncating(activity(), true));
        assertFalse(job.checkTruncating(event(), true));
    }
}
