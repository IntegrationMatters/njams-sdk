package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;

public class SenderStartupSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void awaitStartupReturnsTrueWhenConnectSucceeds() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        assertTrue(s.isGroupConnected());
    }

    @Test
    public void awaitStartupReturnsFalseWhenConnectFails() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.beginConnect();
        assertFalse(s.awaitStartup(5000));
        assertFalse(s.isGroupConnected());
    }

    @Test
    public void awaitStartupTimesOutWhileConnectBlocks() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.beginConnect();
        assertFalse("blocked connect must not report success within the timeout", s.awaitStartup(200));
        // releasing afterwards lets the daemon finish without affecting the (already-returned) result
        LifecycleTestTransport.releaseBlockedConnect();
    }
}
