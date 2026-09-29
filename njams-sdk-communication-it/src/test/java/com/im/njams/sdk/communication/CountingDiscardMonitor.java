package com.im.njams.sdk.communication;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts every message the SDK reports as discarded. Lives in the SDK's own package only because
 * {@link DiscardMonitor#setInstance(DiscardMonitor)} is a package-private test seam. Use it through
 * {@code com.im.njams.sdk.it.support.DiscardObserver}, which guarantees the real monitor is restored.
 */
public class CountingDiscardMonitor extends DiscardMonitor {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    protected void recordDiscard() {
        count.incrementAndGet();
    }

    public int count() {
        return count.get();
    }

    public void install() {
        setInstance(this);
    }

    public static void restore() {
        setInstance(null);
    }
}
