package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

public class SenderStartupSpecTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    private static AbstractSender freshSender() {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        return s;
    }

    @Test
    public void awaitStartupReturnsTrueWhenConnectSucceeds() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertTrue(s.awaitStartup(5000));
        assertTrue(s.isConnected());
    }

    @Test
    public void awaitStartupReturnsFalseWhenConnectFails() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertFalse(s.awaitStartup(5000));
        assertFalse(s.isConnected());
    }

    @Test
    public void awaitStartupTimesOutWhileConnectBlocks() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        AbstractSender s = freshSender();
        s.beginConnect();
        assertFalse("blocked connect must not report success within the timeout", s.awaitStartup(200));
        // releasing afterwards lets the daemon finish without affecting the (already-returned) result
        LifecycleTestTransport.releaseBlockedConnect();
    }
}
