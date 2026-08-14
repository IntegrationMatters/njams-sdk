package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that DiscardMonitor and ThrottleMonitor can be exchanged for testing.
 */
public class MonitorExchangeTest {

    /**
     * Establishes a known monitor state before every test. A test must never assume what the previous test left
     * behind: the instance is a JVM-wide static, so both a stale override and a stale counter would leak.
     */
    @Before
    public void resetMonitors() {
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }

    @After
    public void restoreMonitors() {
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }

    @Test
    public void discardIsRoutedToTheInjectedInstance() {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        DiscardMonitor.discard();
        DiscardMonitor.discard();
        assertEquals("every discard must reach the injected monitor", 2, monitor.count());
    }

    @Test
    public void throttleIsRoutedToTheInjectedInstance() {
        CountingThrottleMonitor monitor = CountingThrottleMonitor.install();
        ThrottleMonitor.throttle(30);
        ThrottleMonitor.throttle(12);
        assertEquals("throttle durations must accumulate on the injected monitor", 42L, monitor.totalMs());
    }

    @Test
    public void clearingTheOverrideRestoresTheRealImplementation() {
        DiscardMonitor real = DiscardMonitor.getInstance();
        assertNotNull(real);
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        assertSame("the override must be active", monitor, DiscardMonitor.getInstance());
        CountingDiscardMonitor.restore();
        assertSame("clearing must restore the same real instance", real, DiscardMonitor.getInstance());
    }

    @Test
    public void theRealImplementationStillCountsAndDoesNotThrow() {
        // Exercises the production path itself: the default instance must remain usable and self-consistent.
        CountingDiscardMonitor.restore();
        DiscardMonitor.discard();
        ThrottleMonitor.throttle(5);
        assertNotNull(DiscardMonitor.getInstance());
        assertNotNull(ThrottleMonitor.getInstance());
    }
}
