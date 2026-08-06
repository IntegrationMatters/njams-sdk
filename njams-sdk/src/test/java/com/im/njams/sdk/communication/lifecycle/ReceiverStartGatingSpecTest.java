package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private Njams newNjams(String senderFailBehavior) {
        Settings s = LifecycleTestTransport.settings();
        if (senderFailBehavior != null) {
            s.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, senderFailBehavior);
        }
        return new Njams(Path.of("test", "receiverGatingRemoved"), "1.0", "test", s);
    }

    @Test
    public void receiverFailureNeverFailsStartUnderTheDefaultFailPolicy() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertTrue("start() must depend only on the sender; the receiver failing must not fail it",
            njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void receiverFailureNeverFailsStartUnderTheReconnectPolicy() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("reconnect");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }

    @Test
    public void senderFailureStillFailsStartRegardlessOfTheReceiver() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = newNjams("fail");
        assertFalse("the sender remains critical: its failure must still fail start()", njams.start());
        assertFalse(njams.isStarted());
    }

    @Test
    public void startSucceedsWhenBothSenderAndReceiverConnect() {
        njams = newNjams("fail");
        assertTrue(njams.start());
        assertTrue(njams.isStarted());
    }
}
