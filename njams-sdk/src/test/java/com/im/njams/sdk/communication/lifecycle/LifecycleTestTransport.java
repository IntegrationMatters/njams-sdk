package com.im.njams.sdk.communication.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.settings.Settings;

/** Test-only controls shared by {@link LifecycleTestSender} and {@link LifecycleTestReceiver}. */
public final class LifecycleTestTransport {

    public static final String NAME = "LIFECYCLE_TEST";

    /** How the next sender connect attempt behaves. */
    public enum ConnectMode { SUCCEED, FAIL, BLOCK }

    private static volatile ConnectMode senderMode = ConnectMode.SUCCEED;
    /** Released to let a BLOCK-ing connect proceed (to SUCCEED). Recreated by {@link #reset()}. */
    private static volatile CountDownLatch blockRelease = new CountDownLatch(1);
    /** Counts down once per sender connect entry (so tests can await that a connect was attempted). */
    private static volatile CountDownLatch connectAttempted = new CountDownLatch(1);
    private static final AtomicInteger senderConnectCount = new AtomicInteger(0);

    /** When armed, a message send blocks on {@link #sendGate} and then fails, dropping the connection. */
    private static volatile boolean sendBlocksThenFails = false;
    /** Fires when a send has entered {@link #awaitSendGateIfArmed()} (so tests know the send is in flight). */
    private static volatile CountDownLatch sendEntered = new CountDownLatch(1);
    /** Released to let a blocked send proceed (to fail). Recreated by {@link #reset()}. */
    private static volatile CountDownLatch sendGate = new CountDownLatch(1);

    private LifecycleTestTransport() {
    }

    /**
     * Stops every {@link LifecycleTestSender} a test created — sets shutdown, cancels reconnect, and releases the
     * BLOCK gate so any blocking connect thread can finish — then clears the sender registry. Call in @After so
     * no daemon reconnect/startup thread survives into a later test where it would count down shared latches.
     */
    public static void shutdownAllSenders() {
        LifecycleTestSender.shutdownAll();
        releaseBlockedConnect();
    }

    /** Resets all controls to the default (SUCCEED) state. Call in @Before. */
    public static void reset() {
        senderMode = ConnectMode.SUCCEED;
        blockRelease = new CountDownLatch(1);
        connectAttempted = new CountDownLatch(1);
        senderConnectCount.set(0);
        sendBlocksThenFails = false;
        sendEntered = new CountDownLatch(1);
        sendGate = new CountDownLatch(1);
    }

    /** Arms the block-then-fail send mode: the next message send blocks on the gate, then fails. */
    public static void armSendBlocksThenFails() {
        sendBlocksThenFails = true;
    }

    /** @return the latch that fires when a send has entered the block-then-fail hook. */
    public static CountDownLatch sendEnteredLatch() {
        return sendEntered;
    }

    /** Lets a blocked send proceed (it then fails and drops the connection). */
    public static void releaseSend() {
        sendGate.countDown();
    }

    // called by LifecycleTestSender's send(...) methods
    static boolean awaitSendGateIfArmed() throws InterruptedException {
        if (!sendBlocksThenFails) {
            return false;
        }
        sendEntered.countDown();
        sendGate.await();
        return true;
    }

    public static void setSenderMode(ConnectMode mode) {
        senderMode = mode;
    }

    /** Lets a BLOCK-ing sender connect finish successfully. */
    public static void releaseBlockedConnect() {
        blockRelease.countDown();
    }

    /** @return a fresh latch that fires the next time a sender connect is attempted. */
    public static CountDownLatch connectAttemptedLatch() {
        return connectAttempted;
    }

    public static int senderConnectCount() {
        return senderConnectCount.get();
    }

    // called by LifecycleTestSender.connect()
    static void onSenderConnect() throws InterruptedException {
        senderConnectCount.incrementAndGet();
        connectAttempted.countDown();
        connectAttempted = new CountDownLatch(1);
        switch (senderMode) {
        case FAIL:
            throw new IllegalStateException("LIFECYCLE_TEST: connect configured to FAIL");
        case BLOCK:
            blockRelease.await();
            return;
        case SUCCEED:
        default:
            return;
        }
    }

    /** Settings selecting this transport with the in-memory configuration provider. */
    public static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, NAME);
        s.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");
        return s;
    }
}
