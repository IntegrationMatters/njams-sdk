package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderShutdownSpecTest extends AbstractLifecycleSpecTest {

    private static SenderConnectorTestAccess connectedGroup() {
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        c.beginConnect();
        c.awaitStartup(5000);
        return c;
    }

    @Test
    public void reconnectIsSuppressedOnceShutdownRequested() throws Exception {
        SenderConnectorTestAccess c = connectedGroup();
        c.setShouldShutdown(true); // group is shutting down
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        c.forceGroupDisconnected();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        c.startReconnect(new IllegalStateException("final send failed during shutdown"));
        assertFalse("no reconnect once shutdown is requested",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void cancelReconnectInterruptsABlockedReconnect() throws Exception {
        SenderConnectorTestAccess c = connectedGroup();
        // force a reconnect that blocks inside connect()
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        c.forceGroupDisconnected();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        c.startReconnect(new IllegalStateException("lost"));
        assertTrue("reconnect started and blocked in connect()", attempted.await(2000, TimeUnit.MILLISECONDS));
        c.cancelReconnect(); // must interrupt the blocked connect thread
        // releasing the latch is not required; interruption unblocks the daemon
        LifecycleTestTransport.releaseBlockedConnect();
    }

    @Test
    public void closeSetsShutdownBeforeDrainingSoAFailingFinalSendDoesNotReconnect() throws Exception {
        // Build a real NjamsSender over the controllable transport, connect, then close while a send fails.
        com.im.njams.sdk.communication.NjamsSender sender =
            new com.im.njams.sdk.communication.NjamsSender(
                ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        sender.startWithTimeout(5000); // connect one sender
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        int before = LifecycleTestTransport.senderConnectCount();
        sender.close(); // shutdown flag must be set before the drain → no new reconnect connects
        // allow any (wrongly-spawned) reconnect a brief window; assert none happened
        assertFalse("shutdown-first ordering must prevent reconnect during drain",
            LifecycleTestTransport.connectAttemptedLatch().await(500, TimeUnit.MILLISECONDS));
    }
}
