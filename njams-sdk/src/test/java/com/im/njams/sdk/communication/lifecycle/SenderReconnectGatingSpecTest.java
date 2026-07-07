package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderReconnectGatingSpecTest extends AbstractLifecycleSpecTest {

    private static AbstractSender freshSender() {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        return s;
    }

    @Test
    public void neverConnectedSenderDoesNotReconnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        AbstractSender s = freshSender();
        // has never connected and reconnect-before-connected was not allowed
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("boom"));
        assertFalse("no reconnect attempt before a first successful connect",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reconnectRunsAfterAPriorSuccessfulConnect() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        // now simulate a mid-processing loss: fail future connects and force disconnect
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ((LifecycleTestSender) s).forceDisconnect();
        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        s.reconnect(new IllegalStateException("lost"));
        assertTrue("reconnect must attempt after prior success",
            attempted.await(2000, TimeUnit.MILLISECONDS));
    }
}
