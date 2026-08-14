package com.im.njams.sdk.communication.lifecycle;

import org.junit.After;
import org.junit.Before;

import com.im.njams.sdk.communication.CountingDiscardMonitor;
import com.im.njams.sdk.communication.CountingThrottleMonitor;
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
        // Pools first: a pool's reconnect loop is only reachable through the pool itself, and it must be stopped
        // before the transport's gates are released, or a late-scheduled loop attempts a connect in the next test.
        SenderPoolTestAccess.shutdownAll();
        LifecycleTestTransport.shutdownAllSenders();
        LifecycleTestTransport.shutdownAllReceivers();
        CountingDiscardMonitor.restore();
        CountingThrottleMonitor.restore();
    }
}
