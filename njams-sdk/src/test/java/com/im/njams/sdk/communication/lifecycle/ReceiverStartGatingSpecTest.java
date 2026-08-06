package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.Settings;

public class ReceiverStartGatingSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    private Njams newNjams(String failBehavior) {
        return newNjams(failBehavior, null);
    }

    private Njams newNjams(String failBehavior, Long connectTimeoutMs) {
        Settings s = LifecycleTestTransport.settings();
        if (failBehavior != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, failBehavior);
        }
        if (connectTimeoutMs != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, String.valueOf(connectTimeoutMs));
        }
        return new Njams(Path.of("test", "receiverGating"), "1.0", "test", s);
    }

    @Test
    public void failFastStartReturnsFalseWhenReceiverCannotConnect() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("fail-fast: start() must return false when the receiver cannot connect", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void reconnectStartReturnsTrueDespiteReceiverInitialFailure() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue("reconnect policy: start() succeeds and retries the receiver in the background", njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenBothSenderAndReceiverConnect() {
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }

    /**
     * Sanity check for the full {@code Njams.start()} integration path (not just the unit-level coverage of
     * {@code Receiver#startWithTimeout(long, boolean)}): a slow (BLOCK) initial receiver connect under the
     * {@code reconnect} policy must not block {@code start()} itself, and must hand off to a background reconnect
     * loop that makes at least one further attempt. Mirrors
     * {@code SenderStartGatingSpecTest#slowThenFailedInitialConnectUnderReconnectPolicyRunsBackgroundLoop}.
     */
    @Test
    public void slowThenFailedInitialReceiverConnectUnderReconnectPolicyRunsBackgroundLoop() throws Exception {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        njams = newNjams("reconnect", 200L);
        assertTrue("reconnect policy: start() succeeds despite a slow initial receiver connect", njams.start());
        assertTrue(njams.isStarted());

        // Latch-driven proof that the receiver's reconnect loop is running: either a second connect attempt
        // already happened, or the fresh connect-attempt latch fires when it does. No fixed sleep is used.
        CountDownLatch secondAttempt = LifecycleTestTransport.receiverConnectAttemptedLatch();
        boolean retried = LifecycleTestTransport.receiverConnectCount() >= 2
            || secondAttempt.await(3000, TimeUnit.MILLISECONDS);
        assertTrue("slow-then-failed initial receiver connect must start the background reconnect loop", retried);
    }
}
