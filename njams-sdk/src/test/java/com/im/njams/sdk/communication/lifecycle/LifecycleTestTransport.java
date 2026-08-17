package com.im.njams.sdk.communication.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static volatile ConnectMode receiverMode = ConnectMode.SUCCEED;
    private static volatile CountDownLatch receiverBlockRelease = new CountDownLatch(1);
    private static volatile CountDownLatch receiverConnectAttempted = new CountDownLatch(1);
    private static final AtomicInteger receiverConnectCount = new AtomicInteger(0);

    /** When armed, the next {@link LifecycleTestReceiver#init} call throws instead of succeeding, then disarms
     * itself so every subsequent {@code init} call succeeds normally. */
    private static final AtomicBoolean receiverConstructionShouldFailOnce = new AtomicBoolean(false);
    /** Set once the armed one-shot failure above has actually fired (i.e., been consumed). Lets tests observe
     * that the failure genuinely happened, instead of racing a connect-count that may simply not have
     * incremented yet. */
    private static volatile boolean receiverConstructionFailureFired = false;

    /** When armed, a message send blocks on {@link #sendGate} and then fails, dropping the connection. */
    private static volatile boolean sendBlocksThenFails = false;
    /** Fires when a send has entered {@link #awaitSendGateIfArmed()} (so tests know the send is in flight). */
    private static volatile CountDownLatch sendEntered = new CountDownLatch(1);
    /** Released to let a blocked send proceed (to fail). Recreated by {@link #reset()}. */
    private static volatile CountDownLatch sendGate = new CountDownLatch(1);
    /** Counts the messages a {@link LifecycleTestSender} actually accepted (i.e. the fail mode was not armed). */
    private static final AtomicInteger successfulSends = new AtomicInteger(0);

    private LifecycleTestTransport() {
    }

    /**
     * Closes every {@link LifecycleTestSender} a test created and releases the BLOCK gate so any blocking connect
     * thread can finish, then clears the sender registry. Call in @After so no daemon startup/reconnect thread
     * survives into a later test where it would count down shared latches. The threads themselves belong to the
     * pool's connector, which {@code SenderPoolTestAccess.shutdownAll()} stops.
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
        successfulSends.set(0);
        receiverMode = ConnectMode.SUCCEED;
        receiverBlockRelease = new CountDownLatch(1);
        receiverConnectAttempted = new CountDownLatch(1);
        receiverConnectCount.set(0);
        receiverConstructionShouldFailOnce.set(false);
        receiverConstructionFailureFired = false;
    }

    /** Arms the block-then-fail send mode: the next message send blocks on the gate, then fails. */
    public static void armSendBlocksThenFails() {
        sendBlocksThenFails = true;
    }

    /** @return the latch that fires when a send has entered the block-then-fail hook. */
    public static CountDownLatch sendEnteredLatch() {
        return sendEntered;
    }

    /**
     * Disarms the block-then-fail send mode again, so the <em>next</em> send succeeds. A send that has already
     * entered the gate still fails: it decided to fail on entry, which is exactly what lets a test hold one
     * failing send in flight while every later send succeeds.
     */
    public static void disarmSendFailure() {
        sendBlocksThenFails = false;
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

    // called by LifecycleTestSender's send(...) methods once a send has completed without failing
    static void onSuccessfulSend() {
        successfulSends.incrementAndGet();
    }

    /** @return how many messages a sender accepted successfully since the last {@link #reset()}. */
    public static int successfulSendCount() {
        return successfulSends.get();
    }

    /**
     * Waits for at least {@code target} successful sends. Dispatch happens on an executor worker, so a bare
     * assertion on {@link #successfulSendCount()} would race it.
     *
     * @param target  the number of successful sends to wait for.
     * @param timeout the maximum time to wait.
     * @param unit    the unit of {@code timeout}.
     * @return {@code true} if the count reached {@code target} within the timeout.
     * @throws InterruptedException if the waiting thread is interrupted.
     */
    public static boolean awaitSuccessfulSends(int target, long timeout, TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (successfulSends.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return successfulSends.get() >= target;
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

    /**
     * Waits for at least {@code target} sender connect attempts. A reconnect election happens on a background
     * connector thread, so a bare assertion on {@link #senderConnectCount()} would race it.
     *
     * @param target  the number of connect attempts to wait for.
     * @param timeout the maximum time to wait.
     * @param unit    the unit of {@code timeout}.
     * @return {@code true} if the count reached {@code target} within the timeout.
     * @throws InterruptedException if the waiting thread is interrupted.
     */
    public static boolean awaitConnectAttempts(int target, long timeout, TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (senderConnectCount.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return senderConnectCount.get() >= target;
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

    public static void setReceiverMode(ConnectMode mode) {
        receiverMode = mode;
    }

    /**
     * Arms a one-shot failure for the next {@link LifecycleTestReceiver#init} call (of that instance or a
     * subclass). Simulates the SDK-375 scenario where {@code CommunicationFactory.getReceiver(Njams)} throws
     * while resolving {@code earlyReceiver} at {@code Njams} construction time, leaving it {@code null} so
     * {@code startReceiver(...)} must (re-)create the receiver from scratch at {@code start()}.
     */
    public static void armReceiverConstructionFailOnce() {
        receiverConstructionShouldFailOnce.set(true);
    }

    // called by LifecycleTestReceiver.init(ClientSettings)
    static boolean consumeReceiverConstructionFailure() {
        boolean fired = receiverConstructionShouldFailOnce.compareAndSet(true, false);
        if (fired) {
            receiverConstructionFailureFired = true;
        }
        return fired;
    }

    /** @return {@code true} once the armed one-shot construction failure (see
     *         {@link #armReceiverConstructionFailOnce()}) has actually fired. Use this instead of racing a
     *         connect-count assertion that may simply not have happened yet. */
    public static boolean receiverConstructionFailureFired() {
        return receiverConstructionFailureFired;
    }

    public static void releaseBlockedReceiverConnect() {
        receiverBlockRelease.countDown();
    }

    public static CountDownLatch receiverConnectAttemptedLatch() {
        return receiverConnectAttempted;
    }

    public static int receiverConnectCount() {
        return receiverConnectCount.get();
    }

    // called by LifecycleTestReceiver.connect()
    static void onReceiverConnect() throws InterruptedException {
        receiverConnectCount.incrementAndGet();
        receiverConnectAttempted.countDown();
        receiverConnectAttempted = new CountDownLatch(1);
        switch (receiverMode) {
        case FAIL:
            throw new IllegalStateException("LIFECYCLE_TEST: receiver connect configured to FAIL");
        case BLOCK:
            receiverBlockRelease.await();
            return;
        case SUCCEED:
        default:
            return;
        }
    }

    /**
     * Stops every {@link LifecycleTestReceiver} a test created — sets shutdown, cancels reconnect, and releases the
     * BLOCK gate so any blocking connect thread can finish — then clears the receiver registry. Call in @After so
     * no daemon reconnect/startup thread survives into a later test where it would count down shared latches.
     */
    public static void shutdownAllReceivers() {
        LifecycleTestReceiver.shutdownAll();
        releaseBlockedReceiverConnect();
    }

    /** Settings selecting this transport with the in-memory configuration provider. */
    public static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, NAME);
        s.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");
        return s;
    }
}
