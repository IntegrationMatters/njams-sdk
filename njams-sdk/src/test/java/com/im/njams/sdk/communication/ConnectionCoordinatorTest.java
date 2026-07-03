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
}
