/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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
    private Settings settings = null;

    @Before
    public void setup() {
        processModel = mock(ProcessModel.class);
        Njams njams = mock(Njams.class);
        when(processModel.getNjams()).thenReturn(njams);
        settings = mock(Settings.class);
        when(njams.getSettings()).thenReturn(settings);
        job = null;
    }

    private void init(int limit, boolean onSuccess) {
        when(settings.getProperty(eq(NjamsSettings.PROPERTY_TRUNCATE_LIMIT))).then(i -> String.valueOf(limit));
        when(settings.getProperty(eq(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS)))
                .then(i -> String.valueOf(onSuccess));
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
