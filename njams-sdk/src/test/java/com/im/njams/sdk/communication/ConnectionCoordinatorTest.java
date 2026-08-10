package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionCoordinatorTest {

    @Test
    public void freshCoordinatorIsNotShuttingDown() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.shouldShutdown());
        assertEquals(0, c.reconnectingCount());
    }

    @Test
    public void beginReconnectCounts() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertEquals(1, c.beginReconnect());
        assertEquals(2, c.beginReconnect());
        assertEquals(2, c.reconnectingCount());
    }

    @Test
    public void markConnectedSignalsTransitionOnce() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.beginReconnect();
        assertTrue("first markConnected is the disconnected->connected transition", c.markConnected());
        assertFalse("second markConnected while already connected is not a transition", c.markConnected());
    }

    @Test
    public void shutdownFlagRoundTrips() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.setShouldShutdown(true);
        assertTrue(c.shouldShutdown());
        c.setShouldShutdown(false);
        assertFalse(c.shouldShutdown());
    }

    @Test
    public void freshCoordinatorHasNeverConnectedAndMustNotReconnect() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.wasEverConnected());
        assertFalse("no reconnect before a first successful connect", c.shouldReconnect());
    }

    @Test
    public void startupConnectMarksEverConnectedAndEnablesReconnect() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertTrue("first startup connect is the transition", c.markStartupConnected());
        assertTrue(c.wasEverConnected());
        assertTrue("reconnect allowed after a prior success", c.shouldReconnect());
        assertFalse("second markStartupConnected while connected is not a transition", c.markStartupConnected());
    }

    @Test
    public void reconnectSuccessAlsoRecordsEverConnected() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.beginReconnect();
        assertTrue(c.markConnected());
        assertTrue(c.wasEverConnected());
    }

    @Test
    public void allowReconnectBeforeConnectedEnablesReconnectWithoutPriorSuccess() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        assertFalse(c.shouldReconnect());
        c.allowReconnectBeforeConnected();
        assertTrue(c.shouldReconnect());
    }

    @Test
    public void shutdownDisablesReconnectEvenAfterConnecting() {
        ConnectionCoordinator c = new ConnectionCoordinator();
        c.markStartupConnected();
        c.setShouldShutdown(true);
        assertFalse("shutdown wins over wasEverConnected", c.shouldReconnect());
    }
}
