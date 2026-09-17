/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Test file - no copyright header required.
 */
package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import org.junit.Test;

import com.im.njams.sdk.AbstractTest;

/**
 * Reproducer tests for SDK-457: a {@link Job} is the shared object across concurrent threads
 * (parallel threads create separate activities in the same job), so job-level state that
 * activities feed into must be safe for concurrent mutation.
 *
 * These tests target the reliably reproducible lost-update races. Pure visibility races
 * (e.g. a plain {@code boolean} flag) cannot be reproduced deterministically because
 * {@link Thread#join()} establishes a happens-before edge that publishes the final value.
 */
public class JobConcurrencyTest extends AbstractTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS_PER_THREAD = 100_000;

    /**
     * {@code JobImpl.addToEstimatedSize} performs a non-atomic read-modify-write. When many
     * threads add concurrently, increments are lost and the estimated message size is undercounted.
     * The estimate drives flushing, so a chronic undercount means the job flushes later (or with a
     * larger-than-intended message) than configured.
     */
    @Test
    public void concurrentAddToEstimatedSizeMustNotLoseUpdates() throws Exception {
        JobImpl job = createDefaultStartedJob();
        long before = job.getEstimatedSize();

        runConcurrently(THREADS, () -> {
            for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                job.addToEstimatedSize(1L);
            }
        });

        assertEquals("every concurrent increment must be accounted for",
                before + (long) THREADS * INCREMENTS_PER_THREAD, job.getEstimatedSize());
    }

    /**
     * Runs the given task on {@code threadCount} threads started as simultaneously as possible
     * (via a {@link CyclicBarrier}) to maximize contention, and rethrows the first failure.
     */
    private static void runConcurrently(int threadCount, Runnable task) throws Exception {
        final CyclicBarrier startGate = new CyclicBarrier(threadCount);
        final List<Throwable> failures = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread(() -> {
                try {
                    startGate.await();
                    task.run();
                } catch (Throwable e) {
                    synchronized (failures) {
                        failures.add(e);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        synchronized (failures) {
            if (!failures.isEmpty()) {
                throw new AssertionError("worker thread failed", failures.get(0));
            }
        }
    }
}
