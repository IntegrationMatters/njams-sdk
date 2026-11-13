package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Specifies {@link DiscardMonitor#recordDiscard()}'s counting contract directly, on a fresh instance.
 */
public class DiscardMonitorTest {

    @Test
    public void countsDiscardsSequentially() {
        DiscardMonitor monitor = new DiscardMonitor();
        monitor.recordDiscard();
        monitor.recordDiscard();
        monitor.recordDiscard();
        assertEquals(3L, monitor.discardCountForTest());
    }

    @Test
    public void concurrentDiscardsLoseNoIncrements() throws InterruptedException {
        final DiscardMonitor monitor = new DiscardMonitor();
        final int threads = 16;
        final int perThread = 5000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        monitor.recordDiscard();
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
        assertEquals("no increment may be lost under concurrent discarding", (long) threads * perThread,
            monitor.discardCountForTest());
    }
}
