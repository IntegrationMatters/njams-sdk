package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.Test;

/**
 * Specifies what a receiver does with SDK-473's recovery signal: a connected receiver cycles its connection, and
 * a receiver whose own lifecycle already owns its state ignores the signal entirely.
 */
public class ReceiverRecoveryCycleSpecTest extends AbstractLifecycleSpecTest {

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

    /** A receiver connected through its normal background startup path. */
    private LifecycleTestReceiver connectedReceiver() {
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        receiver.beginConnect();
        awaitTrue("the receiver must connect", 5000, receiver::isConnected);
        return receiver;
    }

    @Test
    public void aConnectedReceiverCyclesItsConnection() {
        LifecycleTestReceiver receiver = connectedReceiver();
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        awaitTrue("the receiver must reconnect after being cycled", 5000,
            () -> LifecycleTestTransport.receiverConnectCount() > connectsBefore && receiver.isConnected());
        assertTrue("the cycle must have gone through stop()", receiver.callOrder().contains("stop()"));
    }

    @Test
    public void aReceiverThatWasNeverConnectedIgnoresTheSignal() {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.FAIL);
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver that never connected must be left to its own startup/reconnect path",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aShuttingDownReceiverIgnoresTheSignal() {
        LifecycleTestReceiver receiver = connectedReceiver();
        receiver.setShouldShutdown(true);
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver being torn down must not be revived by the signal",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aReceiverAlreadyReconnectingIgnoresTheSignal() throws Exception {
        LifecycleTestReceiver receiver = connectedReceiver();
        // Park the receiver's own reconnect loop inside connect(), so it is demonstrably in flight.
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        // Capture the latch BEFORE triggering: onReceiverConnect() counts the current latch down and immediately
        // replaces it, so a latch fetched afterwards may be the fresh, uncounted one and would never fire.
        CountDownLatch reconnectEnteredConnect = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.onException(new IllegalStateException("its own detected loss"));
        assertTrue("the receiver's own reconnect must have entered connect()",
            reconnectEnteredConnect.await(5, TimeUnit.SECONDS));
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver already running its own reconnect must not be cycled a second time",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }

    @Test
    public void aReceiverStillConnectingForTheFirstTimeIgnoresTheSignal() throws Exception {
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        // Capture the latch BEFORE triggering: onReceiverConnect() counts the current latch down and immediately
        // replaces it, so a latch fetched afterwards may be the fresh, uncounted one and would never fire.
        CountDownLatch startupEnteredConnect = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.beginConnect();
        assertTrue("the receiver's startup connect must have entered connect()",
            startupEnteredConnect.await(5, TimeUnit.SECONDS));
        int connectsBefore = LifecycleTestTransport.receiverConnectCount();

        receiver.onSenderGroupRecovered();

        assertEquals("a receiver still connecting for the first time must not be cycled",
            connectsBefore, LifecycleTestTransport.receiverConnectCount());
    }
}
