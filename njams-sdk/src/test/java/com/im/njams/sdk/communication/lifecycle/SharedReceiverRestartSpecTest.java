package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.Settings;

/**
 * End-to-end coverage of a real {@code ShareableReceiver} through {@code Njams}, for the shared-communications
 * case of the SDK-375 restart fix. {@code CommunicationFactory.sharedReceivers} is a {@code static} cache with no
 * removal path: once the last {@code Njams} sharing a receiver stopped it, that dead instance (its
 * {@code connectBegun} flag already consumed, its {@code shouldShutdown} flag already set) stayed cached forever,
 * so a later {@code Njams} in the same JVM was handed that unusable instance. These tests exercise the eviction
 * fix, and guard the existing {@code removeNjams()} "last user" gating against regression.
 */
public class SharedReceiverRestartSpecTest extends AbstractLifecycleSpecTest {

    private Njams njamsA;
    private Njams njamsB;

    @After
    public void tearDown() {
        if (njamsA != null && njamsA.isStarted()) {
            njamsA.stop();
        }
        if (njamsB != null && njamsB.isStarted()) {
            njamsB.stop();
        }
        // The instance-tracking registry is test-local bookkeeping only; the shared receivers themselves are
        // expected to already be evicted from CommunicationFactory's cache by the stop() calls above.
        SharedLifecycleTestReceiver.clearInstanceRegistry();
    }

    private static Settings sharedSettings() {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        return s;
    }

    private static void awaitTrue(String message, long timeoutMs, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting: " + message, e);
            }
        }
        assertTrue(message, condition.getAsBoolean());
    }

    @Test
    public void newInstanceAfterLastSharedUserStoppedGetsAFreshConnectedReceiver() {
        njamsA = new Njams(Path.of("test", "sharedRestartA"), "1.0", "test", sharedSettings());
        assertTrue(njamsA.start());

        SharedLifecycleTestReceiver first = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull(first);
        awaitTrue("first shared receiver must connect", 2000, first::isConnected);

        assertTrue("njamsA is the only (last) user; stop() must fully stop it", njamsA.stop());

        njamsB = new Njams(Path.of("test", "sharedRestartB"), "1.0", "test", sharedSettings());
        assertTrue(njamsB.start());

        SharedLifecycleTestReceiver second = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull("a new Njams must (re-)create a shared receiver", second);
        assertNotSame("the evicted, dead instance must never be reused -- its connectBegun/shouldShutdown flags "
            + "can never allow it to connect again", first, second);
        awaitTrue("the fresh shared receiver handed to the new Njams must actually connect -- this is the "
                + "SDK-375 eviction bug: without eviction, the stale cached instance is reused and can never "
                + "connect again",
            2000, second::isConnected);
    }

    @Test
    public void firstOfTwoSharedUsersStoppingDoesNotShutDownTheSharedReceiver() {
        Settings s = sharedSettings();
        njamsA = new Njams(Path.of("test", "sharedKeepA"), "1.0", "test", s);
        njamsB = new Njams(Path.of("test", "sharedKeepB"), "1.0", "test", s);
        assertTrue(njamsA.start());
        assertTrue(njamsB.start());

        SharedLifecycleTestReceiver receiver = SharedLifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);
        awaitTrue("shared receiver must connect", 2000, receiver::isConnected);

        assertTrue("njamsA stopping while njamsB still uses the receiver must still report stopped for njamsA",
            njamsA.stop());

        assertTrue("the shared receiver must remain connected for the still-registered njamsB",
            receiver.isConnected());

        // If njamsA.stop() had wrongly shut the shared receiver down, a forced disconnect + reconnect trigger
        // would now be refused (reconnect() early-returns once shouldShutdown() is true).
        receiver.forceDisconnect();
        receiver.onException(new IllegalStateException("simulated disconnect while njamsB still shares this"));
        awaitTrue("the still-shared receiver must still be able to reconnect -- proves it was not shut down "
                + "when the non-last user (njamsA) stopped",
            2000, receiver::isConnected);

        assertFalse("njamsA must no longer be started", njamsA.isStarted());
        assertTrue("njamsB must remain unaffected", njamsB.isStarted());
    }
}
