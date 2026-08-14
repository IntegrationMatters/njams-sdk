package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Verifies that {@link NjamsSender#close()} sets the group shutdown flag <em>before</em> draining the executor, so a
 * failing final send that runs during the drain does not spawn a new reconnect. Lives in the {@code communication}
 * package so it can observe {@link NjamsSender#getExecutor()} for a race-free hand-off (no sleeps).
 */
public class SenderCloseOrderingSpecTest {

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
    }

    @Test
    public void failingFinalSendDuringDrainDoesNotReconnect() throws Exception {
        NjamsSender sender =
            new NjamsSender(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        sender.startWithTimeout(5000); // connect one sender; group is now "was ever connected"

        // A reconnect (if wrongly spawned) attempts connect in FAIL mode, which fires connectAttempted.
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        // The next send blocks inside send() until released, then fails and drops the connection.
        LifecycleTestTransport.armSendBlocksThenFails();

        CountDownLatch sendInFlight = LifecycleTestTransport.sendEnteredLatch();
        sender.send(new LogMessage(), "session-id"); // enqueued; a pool worker will block in send()
        assertTrue("send task must be executing inside a pool worker",
            sendInFlight.await(5000, TimeUnit.MILLISECONDS));

        // Close on a background thread: it runs beginShutdown() -> executor.shutdown() -> awaitTermination (which
        // blocks on the held send). The held send is only released once the executor has entered shutdown, so on the
        // fixed ordering the shutdown flag is guaranteed already set (beginShutdown precedes executor.shutdown()).
        Thread closer = new Thread(sender::close, "test-closer");
        closer.start();

        final long deadline = System.currentTimeMillis() + 5000;
        while (!sender.getExecutor().isShutdown() && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
        assertTrue("close() must have entered the executor drain", sender.getExecutor().isShutdown());

        CountDownLatch attempted = LifecycleTestTransport.connectAttemptedLatch();
        // held send now fails during the drain -> NjamsSender.dispatch -> SenderPool.reportFailure -> reconnect
        LifecycleTestTransport.releaseSend();

        assertFalse("shutdown-first ordering must prevent reconnect during drain",
            attempted.await(1000, TimeUnit.MILLISECONDS));

        closer.join(15000);
    }
}
