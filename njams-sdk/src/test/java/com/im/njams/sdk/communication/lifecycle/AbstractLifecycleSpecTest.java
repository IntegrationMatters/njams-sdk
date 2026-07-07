package com.im.njams.sdk.communication.lifecycle;

import org.junit.After;
import org.junit.Before;

/**
 * Shared lifecycle-spec test harness. Resets the controllable transport before each test and, crucially, stops
 * every sender a test spawned afterwards. Without the teardown, a daemon reconnect/startup thread from one test
 * survives into the next and counts down the shared static latches, making later tests fail non-deterministically
 * (and potentially spamming sender creation). JUnit runs this base {@code @Before} before, and this base
 * {@code @After} after, any override methods in subclasses, so subclasses may add their own setup/teardown freely.
 */
public abstract class AbstractLifecycleSpecTest {

    @Before
    public void resetLifecycleTransport() {
        LifecycleTestTransport.reset();
    }

    @After
    public void stopLifecycleSenders() {
        LifecycleTestTransport.shutdownAllSenders();
    }
}
