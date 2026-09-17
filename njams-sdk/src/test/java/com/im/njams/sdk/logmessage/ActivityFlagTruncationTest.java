package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.serializer.SerializerResult;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * SDK-463: input/output truncation is driven by the serializer's {@link SerializerResult#truncated()}
 * flag, not by a length comparison. A serializer that reports truncation must cause the stored
 * value to carry the truncated-suffix even when the (short) value is within the length limit.
 */
public class ActivityFlagTruncationTest extends AbstractTest {

    private ActivityImpl prepare(String mode, int limit) {
        ClientSettings settings = njams.getSettings();
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, mode);
        settings.put(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, String.valueOf(limit));
        JobImpl job = createDefaultJob();
        job.setDeepTrace(true);
        job.start();
        return (ActivityImpl) createDefaultActivity(job);
    }

    @Test
    public void inputSuffixIsDrivenByTheTruncationFlagNotLength() {
        // serializer reports truncated == true although the produced value is far shorter than the limit
        njams.addSerializer(String.class, (value, sizeLimit) -> new SerializerResult("short", true));
        ActivityImpl activity = prepare("truncate", 100);

        activity.processInput("anything");

        assertEquals("short" + JobImpl.PAYLOAD_TRUNCATED_SUFFIX, activity.getInput());
    }

    @Test
    public void noSuffixWhenSerializerReportsNotTruncated() {
        njams.addSerializer(String.class, (value, sizeLimit) -> new SerializerResult("ok", false));
        ActivityImpl activity = prepare("truncate", 100);

        activity.processOutput("anything");

        assertEquals("ok", activity.getOutput());
    }
}
