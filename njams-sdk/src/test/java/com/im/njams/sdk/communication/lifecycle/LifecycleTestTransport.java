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

    /** When armed, the next {@link LifecycleTestReceiver#stop()} call blocks on {@link #receiverStopGate} until
     * released, then disarms itself. Simulates a slow/blocking transport shutdown (e.g. closing an
     * already-dead connection) for tests proving a caller must not be blocked by it. */
    private static volatile boolean receiverStopBlocks = false;
    /** Fires once a blocked {@code stop()} call has entered the gate. */
    private static volatile CountDownLatch receiverStopEntered = new CountDownLatch(1);
    /** Released to let a blocked {@code stop()} call proceed. */
    private static volatile CountDownLatch receiverStopGate = new CountDownLatch(1);

    /** When armed, the next {@link LifecycleTestReceiver#init} call throws instead of succeeding, then disarms
     * itself so every subsequent {@code init} call succeeds normally. */
    private static final AtomicBoolean receiverConstructionShouldFailOnce = new AtomicBoolean(false);
    /** Set once the armed one-shot failure above has actually fired (i.e., been consumed). Lets tests observe
     * that the failure genuinely happened, instead of racing a connect-count that may simply not have
     * incremented yet. */
    private static volatile boolean receiverConstructionFailureFired = false;

    /** When armed, a message send blocks on {@link #sendGate} and then fails, dropping the connection. */
    private static volatile boolean sendBlocksThenFails = false;
    /**
     * When armed, a message send blocks on {@link #sendGate} and then <em>succeeds</em>, mirroring
     * {@link #sendBlocksThenFails} but for forcing several sends to be genuinely in flight at once (e.g. so each
     * independently acquires/creates its own connected sender instead of one finishing fast enough for a later one
     * to reuse it), rather than to simulate an outage.
     */
    private static volatile boolean sendBlocksThenSucceeds = false;
    /** Fires when a send has entered {@link #awaitSendGateIfArmed()} (so tests know the send is in flight). */
    private static volatile CountDownLatch sendEntered = new CountDownLatch(1);
    /** Released to let a blocked send proceed (to fail or succeed). Recreated by {@link #reset()} and
     *  {@link #rearmSendGate()}. */
    private static volatile CountDownLatch sendGate = new CountDownLatch(1);
    /** Counts sends currently parked in {@link #awaitSendGateIfArmed()} since the last {@link #reset()} or
     *  {@link #rearmSendGate()}. */
    private static final AtomicInteger sendGateEntries = new AtomicInteger(0);
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
        sendBlocksThenSucceeds = false;
        sendEntered = new CountDownLatch(1);
        sendGate = new CountDownLatch(1);
        sendGateEntries.set(0);
        successfulSends.set(0);
        receiverMode = ConnectMode.SUCCEED;
        receiverBlockRelease = new CountDownLatch(1);
        receiverConnectAttempted = new CountDownLatch(1);
        receiverConnectCount.set(0);
        receiverConstructionShouldFailOnce.set(false);
        receiverConstructionFailureFired = false;
        receiverStopBlocks = false;
        receiverStopEntered = new CountDownLatch(1);
        receiverStopGate = new CountDownLatch(1);
    }

    /** Arms the block-then-fail send mode: the next message send blocks on the gate, then fails. */
    public static void armSendBlocksThenFails() {
        sendBlocksThenFails = true;
    }

    /**
     * Arms the block-then-succeed send mode: the next message send(s) block on the gate, then succeed once
     * released. Unlike {@link #armSendBlocksThenFails()}, this is not for simulating an outage but for forcing
     * several sends to be genuinely concurrent — e.g. to warm a pool with N distinct connected senders before a
     * later outage phase, where a send finishing too fast could let a later one reuse its sender instead of
     * creating its own.
     */
    public static void armSendBlocksThenSucceeds() {
        sendBlocksThenSucceeds = true;
    }

    /** @return the latch that fires when a send has entered the block gate (either block-then-fail or
     *          block-then-succeed mode). */
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

    /** Disarms the block-then-succeed send mode again, mirroring {@link #disarmSendFailure()}. */
    public static void disarmSendSuccessBlock() {
        sendBlocksThenSucceeds = false;
    }

    /**
     * Re-arms the send gate for a second block/release cycle within the same test (e.g. a block-then-succeed
     * warm-up phase followed by a separate block-then-fail outage phase). Recreates the gate and the entered
     * latch/counter, so a later {@link #releaseSend()} only releases sends that enter after this call, and
     * {@link #blockedSendCount()}/{@link #awaitBlockedSends(int, long, TimeUnit)} count only entries since this
     * call rather than accumulating across both cycles.
     */
    public static void rearmSendGate() {
        sendEntered = new CountDownLatch(1);
        sendGate = new CountDownLatch(1);
        sendGateEntries.set(0);
    }

    /** Lets every currently-blocked send proceed (each then either fails or succeeds, per whichever mode it
     *  entered under). */
    public static void releaseSend() {
        sendGate.countDown();
    }

    /** @return how many sends have entered the block gate since the last {@link #reset()} or
     *          {@link #rearmSendGate()}. */
    public static int blockedSendCount() {
        return sendGateEntries.get();
    }

    /**
     * Waits for at least {@code target} sends to have entered the block gate. Dispatch happens on executor worker
     * threads, so a bare assertion on {@link #blockedSendCount()} would race them.
     *
     * @param target  the number of blocked-gate entries to wait for.
     * @param timeout the maximum time to wait.
     * @param unit    the unit of {@code timeout}.
     * @return {@code true} if the count reached {@code target} within the timeout.
     * @throws InterruptedException if the waiting thread is interrupted.
     */
    public static boolean awaitBlockedSends(int target, long timeout, TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (sendGateEntries.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return sendGateEntries.get() >= target;
    }

    // called by LifecycleTestSender's send(...) methods
    static boolean awaitSendGateIfArmed() throws InterruptedException {
        if (!sendBlocksThenFails && !sendBlocksThenSucceeds) {
            return false;
        }
        // Capture the outcome on entry, before blocking: this is what lets a test hold one failing send in flight
        // while a later send (under a different mode) succeeds, exactly like the pre-existing block-then-fail
        // semantics this generalizes.
        final boolean shouldFail = sendBlocksThenFails;
        sendGateEntries.incrementAndGet();
        sendEntered.countDown();
        sendGate.await();
        return shouldFail;
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

    /** Arms the block-on-stop mode: the next {@link LifecycleTestReceiver#stop()} call blocks until
     * {@link #releaseReceiverStop()} is called. */
    public static void armReceiverStopBlocks() {
        receiverStopBlocks = true;
    }

    /** @return the latch that fires once a blocked {@code stop()} call has entered the gate. */
    public static CountDownLatch receiverStopEnteredLatch() {
        return receiverStopEntered;
    }

    /** Lets a currently-blocked {@code stop()} call proceed. */
    public static void releaseReceiverStop() {
        receiverStopGate.countDown();
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

    // called by LifecycleTestReceiver.stop()
    static void onReceiverStop() throws InterruptedException {
        if (receiverStopBlocks) {
            receiverStopBlocks = false;
            receiverStopEntered.countDown();
            receiverStopGate.await();
        }
    }

    /**
     * Stops every {@link LifecycleTestReceiver} a test created — sets shutdown, cancels reconnect, and releases the
     * BLOCK-connect and BLOCK-stop gates so any blocking connect or stop thread can finish — then clears the
     * receiver registry. Call in @After so no daemon reconnect/startup thread survives into a later test where it
     * would count down shared latches.
     */
    public static void shutdownAllReceivers() {
        LifecycleTestReceiver.shutdownAll();
        releaseBlockedReceiverConnect();
        releaseReceiverStop();
    }

    /** Settings selecting this transport with the in-memory configuration provider. */
    public static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, NAME);
        s.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");
        return s;
    }
}
