package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * SDK-420: opt-in application of the payload limit to start data. The job's {@code $njams_recorded}
 * flag is cleared to {@code false} only when start data is actually truncated or discarded.
 */
public class StartDataLimitTest extends AbstractTest {

    private static final String RECORDED = "$njams_recorded";

    /** Configures the payload limit and the start-data opt-in, then returns a started job + activity. */
    private ActivityImpl prepare(String mode, int limit, boolean applyToStartData) {
        ClientSettings settings = njams.getSettings();
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, mode);
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, String.valueOf(limit));
        settings.put(NjamsSettings.PROPERTY_APPLY_PAYLOAD_LIMIT_TO_START_DATA, String.valueOf(applyToStartData));
        JobImpl job = createDefaultStartedJob();
        return (ActivityImpl) createDefaultActivity(job);
    }

    @Test
    public void settingOffKeepsFullStartDataAndRecorded() {
        ActivityImpl activity = prepare("truncate", 10, false);

        activity.processStartData("01234567890123456789"); // 20 chars, well over the limit

        assertEquals("01234567890123456789", activity.getStartData());
        assertEquals("true", activity.getJob().getAttributes().get(RECORDED));
    }

    @Test
    public void withinLimitKeepsFullStartDataAndRecorded() {
        ActivityImpl activity = prepare("truncate", 100, true);

        activity.processStartData("short");

        assertEquals("short", activity.getStartData());
        assertEquals("true", activity.getJob().getAttributes().get(RECORDED));
    }

    @Test
    public void truncatedStartDataClearsRecorded() {
        ActivityImpl activity = prepare("truncate", 10, true);

        activity.processStartData("01234567890123456789");

        assertEquals("0123456789" + JobImpl.PAYLOAD_TRUNCATED_SUFFIX, activity.getStartData());
        assertEquals("false", activity.getJob().getAttributes().get(RECORDED));
    }

    @Test
    public void discardedStartDataClearsRecorded() {
        ActivityImpl activity = prepare("discard", 10, true);

        activity.processStartData("01234567890123456789");

        assertEquals(JobImpl.PAYLOAD_DISCARDED_MESSAGE, activity.getStartData());
        assertEquals("false", activity.getJob().getAttributes().get(RECORDED));
    }

    @Test
    public void setStartDataStringIsAlsoLimitedAndClearsRecorded() {
        ActivityImpl activity = prepare("truncate", 10, true);

        activity.setStartData("01234567890123456789");

        assertEquals("0123456789" + JobImpl.PAYLOAD_TRUNCATED_SUFFIX, activity.getStartData());
        assertEquals("false", activity.getJob().getAttributes().get(RECORDED));
    }
}
