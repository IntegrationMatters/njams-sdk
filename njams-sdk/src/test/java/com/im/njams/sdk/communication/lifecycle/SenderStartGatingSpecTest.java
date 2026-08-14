package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
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

    /**
     * The whole point of the {@code reconnect} startup policy is that the server may simply not be up yet: the
     * client starts anyway and is expected to recover on its own once the server appears. So the group's single
     * background reconnect loop must still be <em>alive</em> after the startup timeout has fired and been handled.
     * <p>
     * Regression guard: handling the timeout used to cancel the reconnect loop it had itself just handed off to,
     * and could not restart it (the interrupted thread is still alive for a moment, so the restart saw a reconnect
     * "in flight" and no-opped), leaving the group permanently unable to reconnect.
     */
    @Test
    public void theBackgroundReconnectLoopSurvivesTheStartupTimeout() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect", 200L);
        assertTrue("reconnect policy: start() succeeds despite the failing initial connect", njams.start());

        // Latch first, then let the server come up: taken afterwards, a loop that connects and exits right away
        // would leave nothing left to observe.
        CountDownLatch nextAttempt = LifecycleTestTransport.connectAttemptedLatch();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue("the group's reconnect loop must still be running after the startup timeout was handled",
            nextAttempt.await(10, TimeUnit.SECONDS));
    }

    /**
     * Same regression, asserted end-to-end instead of on the reconnect loop's liveness: with the {@code none}
     * discard policy a message dispatched while the group is down is retained, so it can only ever be delivered if
     * the group really does recover by itself after the startup timeout.
     */
    @Test
    public void aRetainedMessageIsDeliveredOnceTheGroupRecoversAfterTheStartupTimeout() throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "reconnect");
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        try {
            assertTrue("reconnect policy: startup reports success and retries in the background",
                sender.startWithTimeout(200));

            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
            sender.send(new LogMessage(), "session-1");

            assertTrue("the group must recover on its own and deliver the retained message",
                LifecycleTestTransport.awaitSuccessfulSends(1, 15, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }
    }

    /**
     * Shared communications: one {@code NjamsSender} is one group shared by several {@link Njams} instances, and it
     * owns exactly one reconnector. A later instance starting fail-fast against that group and timing out must
     * therefore not cancel the reconnect an earlier instance depends on — it may only give up on itself.
     */
    @Test
    public void aFailFastStartupTimeoutMustNotKillTheGroupsRunningReconnect() throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        NjamsSender shared = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        try {
            // First client, 'reconnect' policy: the group is now retrying in the background.
            assertTrue(shared.startWithTimeout(200, true));
            // Second client, fail-fast, against the very same group: it must fail its own startup ...
            assertFalse(shared.startWithTimeout(200, false));

            // ... without taking the first client's reconnect down with it.
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
            shared.send(new LogMessage(), "session-1");
            assertTrue("a fail-fast startup timeout must leave the group's reconnect loop running",
                LifecycleTestTransport.awaitSuccessfulSends(1, 15, TimeUnit.SECONDS));
        } finally {
            shared.close();
        }
    }
}
