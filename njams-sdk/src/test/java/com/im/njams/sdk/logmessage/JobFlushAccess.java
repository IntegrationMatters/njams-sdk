package com.im.njams.sdk.logmessage;

/**
 * Test accessor for the package-private, SDK-internal flush method of {@link JobImpl}, for tests located in other
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
}
