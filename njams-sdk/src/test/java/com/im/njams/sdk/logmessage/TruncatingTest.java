package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class TruncatingTest {

    private JobImpl job = null;
    private final Random random = new Random();
    private ProcessModel processModel = null;
    private int limit = 10;

    @Before
    public void setup() {
        processModel = mock(ProcessModel.class);
        Njams njams = mock(Njams.class);
        when(processModel.getNjams()).thenReturn(njams);
        Settings settings = mock(Settings.class);
        when(njams.getSettings()).thenReturn(settings);
        when(settings.getProperty(eq(JobImpl.TRUNCATE_LIMIT))).then(i -> String.valueOf(limit));
        job = null;
    }

    private void init(int limit) {
        this.limit = limit;
        job = new JobImpl(processModel, "4711", "4812");
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
        init(10);
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));

        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));

        // no more activities
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));

        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));

        // no more events
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));
        assertFalse(job.checkTruncating(activity()));

        assertFalse(job.checkTruncating(event()));
        assertFalse(job.checkTruncating(event()));
        assertFalse(job.checkTruncating(event()));
        assertFalse(job.checkTruncating(event()));
        assertFalse(job.checkTruncating(event()));
    }

    @Test
    public void testAllEvents() {
        init(10);
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));

        assertFalse(job.checkTruncating(event()));
        assertFalse(job.checkTruncating(activity()));
    }

    @Test
    public void testDisabled() {
        init(0);
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(activity()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));
        assertTrue(job.checkTruncating(event()));

    }

}
