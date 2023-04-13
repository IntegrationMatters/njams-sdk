package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

public class LimitPayloadTest extends AbstractTest {

    private Activity activity = null;

    public void init(String mode, int limit) {
        init(mode, String.valueOf(limit));
    }

    public void init(String mode, String limit) {
        Settings settings = njams.getSettings();
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, mode);
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, limit);
        JobImpl job = createDefaultJob();
        job.setDeepTrace(true);
        job.start();
        activity = createDefaultActivity(job);
    }

    @Test
    public void testDiscard() {
        init("discard", 10);
        activity.setEventPayload("Hello");
        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
    }

    @Test
    public void testTruncate() {
        init("truncate", 10);
        activity.setEventPayload("Hello");
        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals("1234567890" + JobImpl.PAYLOAD_TRUNCATED_SUFFIX, activity.getEventPayload());
    }

    @Test
    public void testLimitToZero() {
        // this internally results in "discard always"
        init("truncate", 0);
        activity.setEventPayload("Hello");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
        init("discard", 0);
        activity.setEventPayload("Hello");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
    }

    @Test
    public void testDefault() {
        init("", 10);
        activity.setEventPayload("Hello");
        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals("12345678901234567890", activity.getEventPayload());
    }

    @Test
    public void testNegativeLimit() {
        // this is internally handled as default, i.e. no-limits!
        init("truncate", -1);
        activity.setEventPayload("Hello");
        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals("12345678901234567890", activity.getEventPayload());
    }

    @Test
    public void testIllegalLimit() {
        // this is internally handled as default, i.e. no-limits!
        init("truncate", "bla");
        activity.setEventPayload("Hello");
        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals("12345678901234567890", activity.getEventPayload());
    }

    @Test
    public void testFields() {
        init("discard", 10);
        activity.setEventPayload("Hello");

        assertEquals("Hello", activity.getEventPayload());
        activity.setEventPayload("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getEventPayload());
        activity.setStackTrace("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getStackTrace());

        ActivityImpl impl = (ActivityImpl) activity;
        activity.processInput("Hello");
        assertEquals("Hello", impl.getInput());
        activity.processInput("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, impl.getInput());
        activity.processOutput("Hello");
        assertEquals("Hello", impl.getOutput());
        activity.processOutput("12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, impl.getOutput());

        activity.addAttribute("key", "Hello");
        assertEquals("Hello", impl.getAttributes().get("key"));
        assertEquals("Hello", impl.getJob().getAttributes().get("key"));
        activity.addAttribute("key", "12345678901234567890");
        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, impl.getJob().getAttributes().get("key"));
    }
}
