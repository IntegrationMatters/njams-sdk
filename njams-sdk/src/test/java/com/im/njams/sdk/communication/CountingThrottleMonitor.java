package com.im.njams.sdk.communication;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Observing ThrottleMonitor for tests: accumulates throttle durations, with no log-level dependency or throttling.
 */
public class CountingThrottleMonitor extends ThrottleMonitor {

    private final AtomicLong ms = new AtomicLong();

    @Override
    protected void recordThrottle(long duration) {
        ms.addAndGet(duration);
    }

    /**
     * @return total milliseconds recorded since this instance was installed.
     */
    public long totalMs() {
        return ms.get();
    }

    /**
     * Waits for at least {@code target} milliseconds. Message dispatch happens on an executor worker, so a bare
     * assertion on {@link #totalMs()} would race it.
     *
     * @return {@code true} if the accumulated time reached {@code target} within the timeout.
     */
    public boolean awaitAtLeast(long target, long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (ms.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return ms.get() >= target;
    }

    /**
     * Installs a fresh counter and returns it.
     */
    public static CountingThrottleMonitor install() {
        CountingThrottleMonitor monitor = new CountingThrottleMonitor();
        ThrottleMonitor.setInstance(monitor);
        return monitor;
    }

    /**
     * Restores the real implementation. Call from test teardown.
     */
    public static void restore() {
        ThrottleMonitor.setInstance(null);
    }
}
