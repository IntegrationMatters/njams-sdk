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

public class SenderStartGatingSpecTest extends AbstractLifecycleSpecTest {

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
        return new Njams(Path.of("test", "gating"), "1.0", "test", s);
    }

    @Test
    public void failFastStartReturnsFalseWhenSenderCannotConnect() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("fail-fast: start() must return false when the sender cannot connect", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void reconnectStartReturnsTrueDespiteInitialFailure() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue("reconnect policy: start() succeeds and retries in the background", njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenSenderConnects() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }

    /**
     * Slow (BLOCK) initial connect under the {@code reconnect} policy: {@code start()} returns true at the short
     * timeout, and the blocked connect must be abandoned (interrupted → fails) and handed off to the background
     * reconnect loop, which makes at least one further connect attempt. Before the fix the blocked startup thread
     * lingered and no reconnect loop ran (only the single startup attempt), so no second attempt was made.
     */
    @Test
    public void slowThenFailedInitialConnectUnderReconnectPolicyRunsBackgroundLoop() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        njams = newNjams("reconnect", 200L);
        assertTrue("reconnect policy: start() succeeds despite a slow initial connect", njams.start());
        assertTrue(njams.isStarted());

        // Latch-driven proof that doReconnect is looping: either a second connect attempt already happened, or the
        // fresh connect-attempt latch fires when it does. No fixed sleep is used for synchronization.
        CountDownLatch secondAttempt = LifecycleTestTransport.connectAttemptedLatch();
        boolean retried = LifecycleTestTransport.senderConnectCount() >= 2
            || secondAttempt.await(3000, TimeUnit.MILLISECONDS);
        assertTrue("slow-then-failed initial connect must start the background reconnect loop", retried);
    }
}
