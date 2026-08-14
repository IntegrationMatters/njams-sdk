package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;

/**
 * Regression coverage for a review finding on {@code SenderConnector} (SDK-472): {@code beginConnect()} and
 * {@code startReconnect(Exception)} each only guarded against their own in-flight state, not the other's, so a
 * startup connect and the reconnect loop could race a second, concurrent {@code connect()} call against the same
 * sender group. Both directions of that race are covered here using the existing {@link LifecycleTestTransport}
 * {@code BLOCK} mode: it parks a connect attempt indefinitely (until released), giving a deterministic window in
 * which to prove no second attempt is started, without racing the reconnect loop's own retry timing.
 */
public class SenderConnectorReconnectGatingSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void beginConnectDoesNotRaceAnAlreadyRunningReconnect() throws InterruptedException {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        SenderConnectorTestAccess c = SenderConnectorTestAccess.createWithReconnectBeforeConnected();

        CountDownLatch firstAttempt = LifecycleTestTransport.connectAttemptedLatch();
        c.startReconnect(new Exception("reconnect started directly for this test"));
        assertTrue("the reconnect loop's connect attempt must begin",
            firstAttempt.await(2000, TimeUnit.MILLISECONDS));

        int countWhileBlocked = LifecycleTestTransport.senderConnectCount();
        // A later caller (e.g. another Njams instance sharing this group) invoking awaitStartup() while the
        // group's single reconnect loop already owns the only connect attempt must not start a competing one.
        assertFalse("must not report success while the reconnect loop is still blocked", c.awaitStartup(0));
        Thread.sleep(200);
        assertEquals("no second connect must be started while the reconnect loop holds the group's only attempt",
            countWhileBlocked, LifecycleTestTransport.senderConnectCount());

        LifecycleTestTransport.releaseBlockedConnect();
        awaitGroupConnected(c);
    }

    @Test
    public void startReconnectDoesNotRaceAnInFlightStartup() throws InterruptedException {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        SenderConnectorTestAccess c = SenderConnectorTestAccess.createWithReconnectBeforeConnected();

        CountDownLatch firstAttempt = LifecycleTestTransport.connectAttemptedLatch();
        Thread awaiter = new Thread(() -> c.awaitStartup(5000));
        awaiter.setDaemon(true);
        awaiter.start();
        assertTrue("the startup connect attempt must begin", firstAttempt.await(2000, TimeUnit.MILLISECONDS));

        int countWhileBlocked = LifecycleTestTransport.senderConnectCount();
        // An externally triggered reconnect (e.g. from a later send() failure once real dispatch is wired up)
        // must not start a second, competing connect while the group's startup connect is still in flight.
        c.startReconnect(new Exception("external reconnect trigger while startup still in flight"));
        Thread.sleep(200);
        assertEquals("no second connect must be started while startup holds the group's only attempt",
            countWhileBlocked, LifecycleTestTransport.senderConnectCount());

        LifecycleTestTransport.releaseBlockedConnect();
        awaiter.join(3000);
        assertTrue("the group must end up connected once the block is released", c.isGroupConnected());
    }

    private void awaitGroupConnected(SenderConnectorTestAccess c) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!c.isGroupConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("the reconnect loop must still complete normally once released", c.isGroupConnected());
    }
}
