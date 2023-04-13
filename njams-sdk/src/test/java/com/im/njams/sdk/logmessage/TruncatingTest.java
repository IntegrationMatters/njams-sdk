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
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class TruncatingTest {

    private JobImpl job = null;
    private final Random random = new Random();
    private ProcessModel processModel = null;
    private int limit = 10;
    private boolean truncateOnSuccess = false;

    @Before
    public void setup() {
        processModel = mock(ProcessModel.class);
        Njams njams = mock(Njams.class);
        when(processModel.getNjams()).thenReturn(njams);
        Settings settings = mock(Settings.class);
        when(njams.getSettings()).thenReturn(settings);
        when(settings.getProperty(eq(NjamsSettings.PROPERTY_TRUNCATE_LIMIT))).then(i -> String.valueOf(limit));
        when(settings.getProperty(eq(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS)))
                .then(i -> String.valueOf(truncateOnSuccess));
        job = null;
    }

    private void init(int limit, boolean onSuccess) {
        this.limit = limit;
        truncateOnSuccess = onSuccess;
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
        init(10, false);
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
        init(10, false);
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
        init(5, false);
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
        init(0, false);
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
        init(10, true);
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
        init(10, true);
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
