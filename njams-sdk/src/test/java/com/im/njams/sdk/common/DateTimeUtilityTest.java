package com.im.njams.sdk.common;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;

import org.junit.Test;

/**
 * Specifies {@link DateTimeUtility#getDurationInMillies(LocalDateTime, LocalDateTime)}.
 */
public class DateTimeUtilityTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 29, 1, 59, 59, 500_000_000);

    @Test
    public void durationInMillis() {
        assertEquals(1500L, DateTimeUtility.getDurationInMillies(START, START.plusNanos(1_500_000_000L)));
    }

    @Test
    public void negativeDurationWhenEndIsBeforeStart() {
        assertEquals(-2000L, DateTimeUtility.getDurationInMillies(START, START.minusSeconds(2)));
    }

    @Test
    public void spansDaysWithoutTimeZoneShift() {
        assertEquals(86_400_000L, DateTimeUtility.getDurationInMillies(START, START.plusDays(1)));
    }

    @Test
    public void nullArgumentsYieldZero() {
        assertEquals(0L, DateTimeUtility.getDurationInMillies(null, START));
        assertEquals(0L, DateTimeUtility.getDurationInMillies(START, null));
    }
}
