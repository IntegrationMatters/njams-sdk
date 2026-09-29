package com.im.njams.sdk.it.support;

import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Observes the SDK's own background threads by name prefix, so a scenario can assert that a reconnect is (or is not)
 * running and that a failed or stopped instance left nothing behind. Thread names are the SDK's:
 * {@code Sender-Startup-*} (initial connect), {@code Sender-Reconnector-*} (the group's single reconnect loop) and
 * {@code Receiver-*} (everything the receiver runs).
 */
public final class SdkThreads {

    public static final String SENDER_STARTUP = "Sender-Startup-";
    public static final String SENDER_RECONNECTOR = "Sender-Reconnector-";
    public static final String RECEIVER = "Receiver-";

    private SdkThreads() {
    }

    /** @return the names of all live threads starting with one of the prefixes. */
    public static Set<String> alive(String... prefixes) {
        return Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> {
                for (String prefix : prefixes) {
                    if (name.startsWith(prefix)) {
                        return true;
                    }
                }
                return false;
            })
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Polls until no thread with one of the prefixes is alive or the timeout elapses.
     *
     * @return the threads still alive at the end (empty on success).
     */
    public static Set<String> awaitNone(Duration timeout, String... prefixes) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Set<String> alive = alive(prefixes);
        while (!alive.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(200);
            alive = alive(prefixes);
        }
        return alive;
    }

    /**
     * Polls until at least one thread with one of the prefixes is alive or the timeout elapses.
     *
     * @return the matching threads at the end (empty if none showed up).
     */
    public static Set<String> awaitAny(Duration timeout, String... prefixes) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Set<String> alive = alive(prefixes);
        while (alive.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(100);
            alive = alive(prefixes);
        }
        return alive;
    }
}
