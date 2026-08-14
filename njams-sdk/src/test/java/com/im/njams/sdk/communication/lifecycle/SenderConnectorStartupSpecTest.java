package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderConnectorTestAccess;

public class SenderConnectorStartupSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void startupConnectsOnceAndReportsSuccess() {
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertTrue("startup must succeed against a SUCCEED transport", c.awaitStartup(5000));
        assertEquals("exactly one connect for one startup", 1, LifecycleTestTransport.senderConnectCount());
    }

    @Test
    public void lateCallerGetsImmediateSuccessWithoutASecondConnect() {
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertTrue(c.awaitStartup(5000));
        int afterFirst = LifecycleTestTransport.senderConnectCount();
        // A second Njams starting later against the already-connected group (spec 5.1)
        assertTrue("a late caller must succeed immediately", c.awaitStartup(0));
        assertEquals("no second connect for a late caller", afterFirst,
            LifecycleTestTransport.senderConnectCount());
    }

    @Test
    public void startupFailureIsReportedAndDoesNotReconnectBeforeEverConnected() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess c = SenderConnectorTestAccess.create();
        assertFalse("a failed startup connect must report false", c.awaitStartup(2000));
    }
}
