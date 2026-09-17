package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link JobSettings}: the per-client snapshot of the job-related settings, with
 * emphasis on the payload-limit parsing branches and the per-instance caching.
 */
public class JobSettingsTest {

    private static ClientSettings settings(Map<String, String> values) {
        return ClientSettings.from(values);
    }

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    @Test
    public void defaultsWhenNothingConfigured() {
        JobSettings s = JobSettings.of(settings(map()));
        assertFalse(s.allErrors);
        assertFalse(s.truncateOnSuccess);
        assertEquals(Integer.MAX_VALUE, s.truncateLimit);
        assertNull(s.payloadLimit);
    }

    @Test
    public void booleanFlagsAreParsed() {
        JobSettings s = JobSettings.of(settings(map(
                NjamsSettings.PROPERTY_LOG_ALL_ERRORS, "true",
                NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, "true")));
        assertTrue(s.allErrors);
        assertTrue(s.truncateOnSuccess);
    }

    @Test
    public void positiveTruncateLimitIsUsed() {
        JobSettings s = JobSettings.of(settings(map(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, "5")));
        assertEquals(5, s.truncateLimit);
    }

    @Test
    public void nonPositiveTruncateLimitFallsBackToMaxValue() {
        JobSettings zero = JobSettings.of(settings(map(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, "0")));
        assertEquals(Integer.MAX_VALUE, zero.truncateLimit);
        JobSettings negative = JobSettings.of(settings(map(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, "-3")));
        assertEquals(Integer.MAX_VALUE, negative.truncateLimit);
    }

    @Test
    public void payloadLimitNullWhenModeBlank() {
        // size set but no mode -> no limit
        JobSettings s = JobSettings.of(settings(map(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "100")));
        assertNull(s.payloadLimit);
    }

    @Test
    public void payloadLimitNullWhenSizeMissing() {
        JobSettings s = JobSettings.of(settings(map(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate")));
        assertNull(s.payloadLimit);
    }

    @Test
    public void payloadLimitTruncateMode() {
        JobSettings s = JobSettings.of(settings(map(
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate",
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "100")));
        assertTrue("truncate mode -> key true", s.payloadLimit.getKey());
        assertEquals(Integer.valueOf(100), s.payloadLimit.getValue());
    }

    @Test
    public void payloadLimitDiscardMode() {
        JobSettings s = JobSettings.of(settings(map(
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "discard",
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "50")));
        assertFalse("discard mode -> key false", s.payloadLimit.getKey());
        assertEquals(Integer.valueOf(50), s.payloadLimit.getValue());
    }

    @Test
    public void payloadLimitSizeZeroAlwaysDiscards() {
        // size 0 discards regardless of the (truncate) mode
        JobSettings s = JobSettings.of(settings(map(
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "truncate",
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "0")));
        assertFalse(s.payloadLimit.getKey());
        assertEquals(Integer.valueOf(0), s.payloadLimit.getValue());
    }

    @Test
    public void payloadLimitNullForUnknownMode() {
        JobSettings s = JobSettings.of(settings(map(
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE, "somethingElse",
                NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, "100")));
        assertNull(s.payloadLimit);
    }

    @Test
    public void ofReturnsCachedInstanceForSameSettings() {
        ClientSettings cs = settings(map());
        assertSame("of() must cache one snapshot per ClientSettings instance",
                JobSettings.of(cs), JobSettings.of(cs));
    }
}
