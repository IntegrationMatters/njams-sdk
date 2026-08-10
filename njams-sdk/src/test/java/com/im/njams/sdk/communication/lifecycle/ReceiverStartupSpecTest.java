package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

/**
 * Reproduces and guards against the SDK-375 restart bug: {@code Njams.startReceiver(NjamsSender)} resolved or
 * created a receiver but never told it to connect. Under the project's start-exactly-once lifecycle contract (an
 * {@code Njams} instance is constructed, started, and eventually stopped exactly once — restarting the same
 * instance is not a supported use case), the only way {@code startReceiver(...)} ever has to (re-)create the
 * receiver itself, rather than simply reuse the constructor's pre-warmed {@code earlyReceiver}, is when that
 * pre-warm failed: receiver construction throwing during {@code Njams}'s constructor, then succeeding when
 * {@code start()} retries it.
 */
public class ReceiverStartupSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
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
    public void receiverIsConnectedAfterTheFirstStart() {
        njams = new Njams(Path.of("test", "receiverStartupFirstStart"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);
        awaitTrue("receiver must connect after the first start()", 2000, receiver::isConnected);
    }

    @Test
    public void receiverConnectsWhenConstructorTimeCreationFailedAndStartRetriesIt() {
        LifecycleTestTransport.armReceiverConstructionFailOnce();

        njams = new Njams(Path.of("test", "receiverStartupConstructionRetry"), "1.0", "test",
            LifecycleTestTransport.settings());
        // CommunicationFactory's SPI lookup constructs-and-discards throwaway probe instances of every
        // registered Receiver class merely to read getName()/check instanceof (see SharedLifecycleTestReceiver's
        // Javadoc), so LifecycleTestReceiver.lastCreated() is not a reliable "did the real earlyReceiver survive"
        // signal here. receiverConnectCount() is: connect() is only ever invoked on a receiver that was actually
        // wired into Njams and told to begin connecting, never on a throwaway probe.
        assertEquals("the constructor's own receiver pre-warm must have failed before ever attempting a "
                + "connection -- otherwise this test would not exercise startReceiver()'s own (re-)creation path",
            0, LifecycleTestTransport.receiverConnectCount());

        assertTrue("start() must succeed despite the receiver having failed to construct earlier", njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("start() must (re-)create the receiver that failed to construct earlier", receiver);
        awaitTrue("the receiver (re-)created by start() must actually be connected -- this is the SDK-375 bug: "
                + "startReceiver() resolved/created the receiver but never told it to connect",
            2000, receiver::isConnected);
    }
}
