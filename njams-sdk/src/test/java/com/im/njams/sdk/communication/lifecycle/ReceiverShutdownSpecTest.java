package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Deliberately narrow in scope: proves {@code Njams.stop()} returns cleanly (no hang, returns {@code true}) when
 * a receiver reconnect could plausibly be in flight, per the plan's own accepted limitation (Part 3 plan, Task 5,
 * "adjust/simplify this test... if this scenario proves hard to trigger black-box through {@code Njams} alone; do
 * not weaken Task 2's test to compensate"). It does <strong>not</strong> regression-test {@code
 * cancelReconnect()}'s interrupt behavior, nor the {@code reallyStopped}/{@code cancelReconnect()} gate inside
 * {@code Njams.stop()} — deleting that gated block would not turn this test red. That regression coverage is
 * Task 2's {@code AbstractReceiverTest#cancelReconnectInterruptsABlockedReconnectThread}'s job, which is
 * white-box and already covers the mechanism directly.
 */
public class ReceiverShutdownSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    /**
     * Simulates a mid-processing receiver connection loss that starts reconnecting and then blocks in
     * {@code connect()} — {@code onException(Exception)} is the exact hook real receiver implementations call on
     * an async transport disconnect (see {@code JmsReceiver#onException}, {@code HttpSseReceiver}'s exception
     * handler, and Kafka's {@code CommandsConsumer} calling {@code receiver.onException(e)}), so triggering it
     * directly on the receiver instance {@code Njams} wired internally reproduces a real disconnect callback
     * rather than a synthetic one. The test's actual purpose is narrower than this setup: prove that
     * {@code Njams.stop()} completes cleanly (no hang, returns {@code true}) while that reconnect is genuinely
     * in flight and blocked.
     */
    @Test
    public void stopCancelsAnInProgressReceiverReconnect() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdown"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        // Arm BLOCK mode before triggering the disconnect, so the background reconnect this spawns genuinely
        // blocks inside connect() instead of racing to finish before stop() runs.
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.onException(new IllegalStateException("connection lost"));
        assertTrue("receiver connect attempted and blocked in connect()", attempted.await(2, TimeUnit.SECONDS));

        boolean stopped = njams.stop();
        assertTrue(stopped);
        assertFalse("must not still be started", njams.isStarted());
    }

    @Test
    public void stopSetsShouldShutdownDirectlyOnTheReceiver() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdownDirect"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        assertTrue(njams.stop());

        // With independent coordinators, a stray reconnect after stop() can only see shouldShutdown() == true if
        // Njams.stop() told this receiver directly — there is no shared instance to observe it via a side effect.
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.reconnect(new IllegalStateException("late failure observed after stop()"));
        assertFalse("stop() must have set shouldShutdown directly on this receiver's own coordinator",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }

    /**
     * Guards the SDK-375 ordering fix: for a non-shareable receiver, {@code Njams.stop()} must signal
     * {@code setShouldShutdown(true)} <em>before</em> calling {@code receiver.stop()}. Without that ordering, a
     * startup connect completing concurrently in the background ({@code AbstractReceiver#beginConnect()}) could
     * observe {@code shouldShutdown() == false} and leave a connection live that {@code stop()} never tears down
     * (nothing was connected yet when {@code stop()} ran) and the flag-check never catches (not yet set). This
     * test proves the deterministic part of that contract — the call order — not the race itself.
     */
    @Test
    public void stopSignalsShutdownBeforeStoppingTheReceiver() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdownOrdering"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        assertTrue(njams.stop());

        assertShutdownSignalledBeforeStop(receiver.callOrder());
    }

    /**
     * Same ordering guarantee as {@link #stopSignalsShutdownBeforeStoppingTheReceiver()}, but through the other
     * entry point that tears down a non-shareable receiver: {@code Njams.stopReceiverAfterStartupFailure(AbstractReceiver)},
     * reached when the sender fails to connect under the default fail-fast startup policy while a receiver has
     * already been constructed.
     */
    @Test
    public void stopReceiverAfterStartupFailureSignalsShutdownBeforeStoppingTheReceiver() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        njams = new Njams(Path.of("test", "receiverShutdownOrderingStartupFailure"), "1.0", "test",
            LifecycleTestTransport.settings());

        assertFalse("sender connect failure must fail start() under the default fail-fast policy", njams.start());
        assertFalse(njams.isStarted());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        assertShutdownSignalledBeforeStop(receiver.callOrder());
    }

    /**
     * The receiver is pre-warmed at construction. When {@code start()} then fails because the sender cannot even be
     * constructed (here: an invalid thread-pool setting), that receiver must be shut down too instead of being left
     * connecting or connected in the background.
     */
    @Test
    public void startFailingOnSenderConstructionShutsTheReceiverDown() {
        ClientSettings settings = LifecycleTestTransport.settings();
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "0");
        njams = new Njams(Path.of("test", "receiverShutdownSenderConstruction"), "1.0", "test", settings);

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        assertFalse("sender construction failure must fail start()", njams.start());

        assertShutdownSignalledBeforeStop(receiver.callOrder());
    }

    /**
     * A reconnect whose {@code connect()} cannot be interrupted (e.g. a JMS handshake) can complete after the
     * receiver was already told to shut down and stopped. The connection it establishes then must be released
     * instead of staying attached for the rest of the JVM's life. Reproduces the shutdown-vs-reconnect race by
     * signalling shutdown and stopping the receiver while its reconnect is blocked inside {@code connect()}, and
     * only then letting that {@code connect()} succeed.
     */
    @Test
    public void aReconnectThatCompletesAfterShutdownReleasesItsConnection() throws Exception {
        njams = new Njams(Path.of("test", "receiverShutdownRace"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());
        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed a receiver reachable through the test registry", receiver);

        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.forceDisconnect();
        receiver.onException(new IllegalStateException("connection lost"));
        assertTrue("receiver connect attempted and blocked in connect()", attempted.await(2, TimeUnit.SECONDS));

        // what Njams.stop() does, minus the interrupt: a connect() that ignores interrupts is not cancelled by it
        receiver.setShouldShutdown(true);
        receiver.stop();
        LifecycleTestTransport.releaseBlockedReceiverConnect();

        // wait for the reconnect to finish; checking isConnected() alone could pass before connect() completed
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (reconnectThreadAlive() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertFalse("the reconnect thread must have ended", reconnectThreadAlive());
        assertFalse("a connection established after shutdown was requested must be released, call order was "
            + receiver.callOrder(), receiver.isConnected());
    }

    private static boolean reconnectThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
            .anyMatch(th -> th.getName().startsWith("Receiver-Sender-Reconnector-Thread"));
    }

    private static void assertShutdownSignalledBeforeStop(List<String> callOrder) {
        int shutdownIndex = callOrder.indexOf("setShouldShutdown(true)");
        int stopIndex = callOrder.indexOf("stop()");
        assertTrue("setShouldShutdown(true) must have been called", shutdownIndex >= 0);
        assertTrue("stop() must have been called", stopIndex >= 0);
        assertTrue("setShouldShutdown(true) must be called before stop(), recorded order was " + callOrder,
            shutdownIndex < stopIndex);
    }
}
