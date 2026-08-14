package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;

public class SenderReconnectGatingSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void neverConnectedSenderDoesNotReconnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        // has never connected and reconnect-before-connected was not allowed
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.startReconnect(new IllegalStateException("boom"));
        assertFalse("no reconnect attempt before a first successful connect",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reconnectRunsAfterAPriorSuccessfulConnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        // now simulate a mid-processing loss: fail future connects and force the group disconnected
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        s.forceGroupDisconnected();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.startReconnect(new IllegalStateException("lost"));
        assertTrue("reconnect must attempt after prior success",
            attempted.await(2000, TimeUnit.MILLISECONDS));
    }
}
