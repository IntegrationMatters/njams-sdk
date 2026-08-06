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
 * Proves that {@code Njams} actually shares the sender group's {@code ConnectionCoordinator} with the receiver
 * (via {@code NjamsSender#wireReceiver(Receiver)}, called from {@code Njams#beginConnect()} and
 * {@code Njams#startReceiver(boolean)}) rather than each side independently reaching the same conclusion.
 * <p>
 * {@code Njams#stop()} never calls {@code receiver.setShouldShutdown(true)} directly — it only closes the sender
 * (which sets the shutdown flag on the sender group's coordinator) and then cancels the receiver's in-progress
 * reconnect. The receiver only learns that the group is shutting down because its {@code coordinator} field *is*
 * the same object as the sender's — i.e. because {@code wireReceiver(...)} was actually called. If it were not,
 * the receiver would keep its own private, never-shut-down coordinator, and a stray reconnect after {@code stop()}
 * would go ahead and attempt a real connect.
 */
public class CoordinatorSharingSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void stopSharesTheSendersShutdownFlagWithTheWiredReceiverEvenThoughNjamsNeverSetsItDirectly()
        throws Exception {
        njams = new Njams(Path.of("test", "coordinatorSharing"), "1.0", "test", LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull("Njams must have constructed and wired a receiver reachable through the test registry",
            receiver);

        assertTrue(njams.stop());

        // Njams.stop() itself never tells the receiver's coordinator to shut down; the only way the receiver's
        // coordinator.shouldShutdown() can already be true here is that it is the SAME coordinator instance the
        // sender's close() just marked as shutting down. reconnect() checks that flag before attempting anything,
        // so a stray reconnect call right after stop() must not attempt a connect if sharing is wired correctly.
        CountDownLatch attempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.reconnect(new IllegalStateException("late failure observed after stop()"));
        assertFalse("a reconnect after stop() must see the coordinator already shut down (shared with the "
                + "sender's coordinator) and must not attempt a connect",
            attempted.await(500, TimeUnit.MILLISECONDS));
    }
}
