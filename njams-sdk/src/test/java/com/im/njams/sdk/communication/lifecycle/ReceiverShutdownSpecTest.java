package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

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
}
