package com.im.njams.sdk.logmessage;

import java.time.LocalDateTime;

/**
 * Test accessor for the package-private, SDK-internal flush methods of {@link JobImpl}, for tests located in other
 * packages.
 */
public final class JobFlushAccess {

    private JobFlushAccess() {
        // utility class
    }

    /**
     * Flushes the given job like the SDK does when the job ends.
     *
     * @param job the job to flush
     */
    public static void flush(Job job) {
        ((JobImpl) job).flush();
    }

    /**
     * Flushes the given job like the SDK's periodic flush task if it is due.
     *
     * @param job the job to flush
     * @param sentBefore flush if the last flush was before this timestamp
     * @param flushSize flush if the estimated message size is greater than this size
     */
    public static void timerFlush(Job job, LocalDateTime sentBefore, long flushSize) {
        ((JobImpl) job).timerFlush(sentBefore, flushSize);
    }
}
