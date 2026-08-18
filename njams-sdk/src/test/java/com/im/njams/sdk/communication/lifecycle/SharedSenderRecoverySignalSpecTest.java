package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies SDK-473 end-to-end under shared communications: one outage of the JVM-wide sender group cycles the
 * one shared receiver exactly once, and a stopped {@code Njams} instance takes its receiver out of the group's
 * listener set instead of leaving it registered for the group's lifetime.
 */
public class SharedSenderRecoverySignalSpecTest extends AbstractLifecycleSpecTest {

    private final List<NjamsSender> taken = new ArrayList<>();
    private final List<Njams> clients = new ArrayList<>();

    /**
     * The shared sender is a reference-counted JVM-wide static: it must be closed exactly as often as it was
     * taken, or it leaks into later tests. See {@code SharedSenderOutageSpecTest}'s equivalent teardown.
     */
    @After
    public void releaseClientsAndSharedSenders() {
        clients.forEach(c -> {
            if (c.isStarted()) {
                c.stop();
            }
        });
        clients.clear();
        taken.forEach(NjamsSender::close);
        taken.clear();
    }

    private static Settings sharedSettings() {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "2");
        // "none" so a message dispatched while the group is reconnecting is retained and retried on the fresh
        // sender once reconnected, instead of being discarded (the default) — see MessageRetentionSpecTest for
        // the same distinction. driveOutageAndRecovery's second phase relies on retention to prove the group
        // really recovered.
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        return s;
    }

    /** Takes a test-held reference on the shared group, so it survives every client stopping. */
    private NjamsSender takeSharedSender() {
        NjamsSender sender =
            NjamsSender.takeSharedSender(ClientSettings.from(sharedSettings().getAllProperties()));
        taken.add(sender);
        return sender;
    }

    private Njams startClient(String name) {
        Njams njams = new Njams(Path.of("test", name), "1.0", "test", sharedSettings());
        clients.add(njams);
        assertTrue("the client must start", njams.start());
        return njams;
    }

    private static void awaitTrue(String message, long timeoutMs, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting: " + message, e);
            }
        }
        assertTrue(message, condition.getAsBoolean());
    }

    /**
     * Drives one real outage through the given group: a send fails, the group's reconnect loop fails at least
     * once (so the evidence gate is satisfied), then connects again.
     */
    private void driveOutageAndRecovery(NjamsSender group) throws InterruptedException {
        LifecycleTestTransport.armSendBlocksThenFails();
        group.send(new LogMessage(), "session");
        assertTrue("a send must reach the transport",
            LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));

        int connectsBeforeOutage = LifecycleTestTransport.senderConnectCount();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        LifecycleTestTransport.releaseSend();
        LifecycleTestTransport.disarmSendFailure();

        assertTrue("the group's reconnect must fail at least once, or the evidence gate is not exercised",
            LifecycleTestTransport.awaitConnectAttempts(connectsBeforeOutage + 1, 15, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
    }

    @Test
    public void oneOutageCyclesTheSharedReceiverExactlyOnce() throws Exception {
        NjamsSender group = takeSharedSender();
        startClient("sharedRecoveryFirst");
        startClient("sharedRecoverySecond");

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull("a shared receiver instance must have been created and wired", receiver);
        awaitTrue("the shared receiver must connect", 5000, receiver::isConnected);
        int receiverConnectsBefore = LifecycleTestTransport.receiverConnectCount();

        driveOutageAndRecovery(group);

        awaitTrue("the shared receiver must be cycled once the group recovers", 20_000,
            () -> LifecycleTestTransport.receiverConnectCount() > receiverConnectsBefore);
        awaitTrue("the shared receiver must be connected again after the cycle", 10_000, receiver::isConnected);
        assertEquals("a shared receiver registered by two clients must be cycled once, not twice",
            receiverConnectsBefore + 1, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aStoppedClientsReceiverIsNoLongerCycled() throws Exception {
        NjamsSender group = takeSharedSender();
        Njams first = startClient("sharedRecoveryStopFirst");
        Njams second = startClient("sharedRecoveryStopSecond");

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);
        awaitTrue("the shared receiver must connect", 5000, receiver::isConnected);

        // Stopping one of two users must NOT deregister the shared receiver: the sibling still uses it.
        first.stop();
        int connectsAfterFirstStop = LifecycleTestTransport.receiverConnectCount();
        driveOutageAndRecovery(group);
        awaitTrue("a shared receiver a sibling still uses must still be cycled", 20_000,
            () -> LifecycleTestTransport.receiverConnectCount() > connectsAfterFirstStop);
        awaitTrue("the shared receiver must be connected again", 10_000, receiver::isConnected);

        // Stopping the last user really stops the receiver, and must take it out of the group's listener set.
        second.stop();
        int connectsAfterLastStop = LifecycleTestTransport.receiverConnectCount();
        int successfulSendsBefore = LifecycleTestTransport.successfulSendCount();
        LifecycleTestTransport.rearmSendGate();
        driveOutageAndRecovery(group);
        // Proving the group really recovered — rather than asserting a condition that is already true — is what
        // makes the receiver-count assertion below meaningful: the message retained across the outage is sent by
        // the fresh sender only once the group is connected again.
        assertTrue("the group must recover a second time and deliver the retained message",
            LifecycleTestTransport.awaitSuccessfulSends(successfulSendsBefore + 1, 20, TimeUnit.SECONDS));
        assertEquals("a stopped client's receiver must no longer be cycled by the group it left",
            connectsAfterLastStop, LifecycleTestTransport.receiverConnectCount());
    }

    /**
     * Regression guard for a final-review finding (M3): the deregistration branch in {@code
     * Njams#stopReceiverAfterStartupFailure} (only reachable when the shared sender group fails to connect at
     * startup under the default {@code fail} startup policy) had no test — every existing test of receiver
     * deregistration above only exercises the normal {@link Njams#stop()} path. Its correctness rests on the
     * {@code sender} field already being set by the time this branch runs, which is only true because
     * {@code start()} calls {@code getSender()} before {@code startReceiver}, which itself runs before the
     * sender's {@code startWithTimeout} check — this pins that ordering so a future refactor cannot silently
     * break it.
     */
    @Test
    public void aClientThatFailedAtStartupNeverHasItsReceiverCycled() throws Exception {
        NjamsSender group = takeSharedSender();
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);

        Njams failed = new Njams(Path.of("test", "sharedRecoveryStartupFail"), "1.0", "test", sharedSettings());
        clients.add(failed);
        assertFalse("the client's startup must fail because the shared sender group cannot connect",
            failed.start());

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull("the receiver must still have been created and registered before the sender's startup "
            + "failure was discovered", receiver);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        // The failed client's own fail-fast startup connect was cancelled, not retried (the group never called
        // allowReconnectBeforeConnected()), so the group itself was never actually connected through its own
        // connector — driveOutageAndRecovery's later reconnect election would find nothing to elect a reconnector
        // for. Establish a real, connector-driven first connection on the group directly (bypassing another
        // Njams/receiver) before driving the outage, exactly mirroring what a normal client's own start() does.
        group.beginConnect();
        assertTrue("the group must connect once the transport is healthy again",
            group.startWithTimeout(5000, false));
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();
        driveOutageAndRecovery(group);

        assertEquals("a receiver whose client failed at startup must have been deregistered and never cycled",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }
}
