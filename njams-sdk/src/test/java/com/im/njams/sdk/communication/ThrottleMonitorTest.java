package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Specifies {@link ThrottleMonitor#recordThrottle(long)}'s accumulation contract directly, on a fresh instance
 * (its constructor is package-visible for exactly this reason) rather than through the {@code setInstance} seam,
 * which is for callers of {@link ThrottleMonitor#throttle(long)} to observe themselves, not for testing this
 * class's own internals.
 */
public class ThrottleMonitorTest {

    @Test
    public void accumulatesThrottleDurationsSequentially() {
        ThrottleMonitor monitor = new ThrottleMonitor();
        monitor.recordThrottle(10);
        monitor.recordThrottle(25);
        monitor.recordThrottle(7);
        assertEquals(42L, monitor.throttleCountForTest());
    }

    @Test
    public void concurrentThrottleReportsLoseNoIncrements() throws InterruptedException {
        final ThrottleMonitor monitor = new ThrottleMonitor();
        final int threads = 16;
        final int perThread = 5000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        monitor.recordThrottle(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals("no increment may be lost under concurrent reporting", (long) threads * perThread,
            monitor.throttleCountForTest());
    }
}
