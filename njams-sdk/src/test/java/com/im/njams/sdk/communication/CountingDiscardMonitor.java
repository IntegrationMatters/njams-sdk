package com.im.njams.sdk.communication;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observing DiscardMonitor for tests: counts every discard, with no log-level dependency or throttling.
 */
public class CountingDiscardMonitor extends DiscardMonitor {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    protected void recordDiscard() {
        count.incrementAndGet();
    }

    /**
     * @return how many discards were recorded since this instance was installed.
     */
    public int count() {
        return count.get();
    }

    /**
     * Waits for at least {@code target} discards. Message dispatch happens on an executor worker, so a bare
     * assertion on {@link #count()} would race it.
     *
     * @return {@code true} if the count reached {@code target} within the timeout.
     */
    public boolean awaitAtLeast(int target, long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (count.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return count.get() >= target;
    }

    /**
     * Installs a fresh counter and returns it.
     */
    public static CountingDiscardMonitor install() {
        CountingDiscardMonitor monitor = new CountingDiscardMonitor();
        DiscardMonitor.setInstance(monitor);
        return monitor;
    }

    /**
     * Restores the real implementation. Call from test teardown.
     */
    public static void restore() {
        DiscardMonitor.setInstance(null);
    }
}
