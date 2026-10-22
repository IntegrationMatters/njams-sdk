package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;

import com.im.njams.sdk.communication.CountingDiscardMonitor;
import com.im.njams.sdk.communication.CountingThrottleMonitor;
import com.im.njams.sdk.communication.SenderConnectorTestAccess;
import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Shared lifecycle-spec test harness. Resets the controllable transport before each test and, crucially, stops
 * every sender a test spawned afterwards. Without the teardown, a daemon reconnect/startup thread from one test
 * survives into the next and counts down the shared static latches, making later tests fail non-deterministically
 * (and potentially spamming sender creation). JUnit runs this base {@code @Before} before, and this base
 * {@code @After} after, any override methods in subclasses, so subclasses may add their own setup/teardown freely.
 * <p>
 * It also owns the lifecycle of the exchangeable {@code DiscardMonitor}/{@code ThrottleMonitor} singletons: both
 * are JVM-wide statics, so a test must never assume their initial state. A <em>fresh</em> counting instance is
 * installed before each test and the real implementation restored afterwards, which is what lets an inheriting
 * test assert an absolute count instead of a before/after delta. Nothing else may install a monitor for these
 * tests — a second install would silently replace the first and leave one of the two references counting nothing.
 */
public abstract class AbstractLifecycleSpecTest {

    /** Upper bound for how long teardown waits for the SDK's sender threads to end. */
    private static final long QUIESCE_TIMEOUT_SECONDS = 10;

    /** Fresh per test, so counts start at zero and no test depends on another's leftovers. */
    protected CountingDiscardMonitor discardMonitor;
    /** Fresh per test, so accumulated throttle time starts at zero. */
    protected CountingThrottleMonitor throttleMonitor;

    @Before
    public void resetLifecycleTransport() {
        LifecycleTestTransport.reset();
        discardMonitor = CountingDiscardMonitor.install();
        throttleMonitor = CountingThrottleMonitor.install();
    }

    @After
    public void stopLifecycleSendersAndReceivers() {
        // Pools and connectors first: a reconnect loop is only reachable through the pool/connector that owns it,
        // and it must be stopped before the transport's gates are released, or a late-scheduled loop attempts a
        // connect in the next test.
        SenderPoolTestAccess.shutdownAll();
        SenderConnectorTestAccess.shutdownAll();
        LifecycleTestTransport.shutdownAllSenders();
        LifecycleTestTransport.shutdownAllReceivers();
        awaitNoSdkCommunicationThreads();
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }

    /**
     * Waits until no SDK sender or receiver thread from this test is still running, interrupting any that
     * linger. Asking is not enough: the bridges above only reach what the test created through them, and a
     * connect loop left running keeps retrying into the next test, where its logging and its connect attempts
     * are counted against state no test established. Only the SDK's own threads are touched.
     */
    private static void awaitNoSdkCommunicationThreads() {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(QUIESCE_TIMEOUT_SECONDS);
        List<Thread> lingering = liveSdkThreads(AbstractLifecycleSpecTest::isCommunicationThread);
        while (!lingering.isEmpty() && System.nanoTime() < deadline) {
            // Repeatedly: the first interrupt is consumed by whatever connect() throws, so the loop would carry
            // on into the next attempt. The one that lands in Thread.sleep ends it.
            lingering.forEach(Thread::interrupt);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            lingering = liveSdkThreads(AbstractLifecycleSpecTest::isCommunicationThread);
        }
    }

    /** @return the names of the SDK's own sender threads that are currently alive. */
    protected static List<String> liveSdkSenderThreadNames() {
        return liveSdkThreads(AbstractLifecycleSpecTest::isSenderThread).stream()
            .map(Thread::getName).collect(Collectors.toList());
    }

    /** @return the names of the SDK's own receiver threads that are currently alive. */
    protected static List<String> liveSdkReceiverThreadNames() {
        return liveSdkThreads(AbstractLifecycleSpecTest::isReceiverThread).stream()
            .map(Thread::getName).collect(Collectors.toList());
    }

    private static List<Thread> liveSdkThreads(Predicate<Thread> matches) {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .filter(matches)
            .collect(Collectors.toList());
    }

    private static boolean isSenderThread(Thread t) {
        return t.getName().startsWith("Sender-Reconnector-") || t.getName().startsWith("Sender-Startup-");
    }

    private static boolean isReceiverThread(Thread t) {
        return t.getName().startsWith("Receiver-");
    }

    private static boolean isCommunicationThread(Thread t) {
        return isSenderThread(t) || isReceiverThread(t);
    }
}
