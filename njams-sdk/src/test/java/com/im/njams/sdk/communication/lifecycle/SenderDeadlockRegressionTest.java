package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.MaxQueueLengthHandler;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Regression guard for design-spec section 7: the pre-SDK-472 approach of re-enqueueing a failed message back onto
 * the executor could deadlock permanently once the queue is full and every worker thread is itself a submitter
 * during an outage. Under the {@code none} discard policy, {@link MaxQueueLengthHandler} blocks the submitting
 * thread until a queue slot frees up, so a worker thread re-submitting its own failed message would block forever
 * once every worker is doing the same thing. The current {@link NjamsSender} design fixes this by retaining a
 * failed message on the same worker thread and retrying it on a freshly acquired sender, never re-submitting it to
 * the executor. If a future change reintroduces re-submission, this test hangs and fails on its hard timeout
 * instead of passing silently.
 */
public class SenderDeadlockRegressionTest extends AbstractLifecycleSpecTest {

    /** Fails rather than hanging CI if the pool cannot make progress after a reconnect. */
    @Test(timeout = 60_000)
    public void theGroupRecoversWithAFullQueueAndEverySenderFailing() throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "2");
        s.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");
        s.put(NjamsSettings.PROPERTY_MAX_QUEUE_LENGTH, "4");
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        try {
            // Every send fails and every connect fails: all workers busy, queue full, group reconnecting.
            LifecycleTestTransport.armSendBlocksThenFails();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
            // Exactly saturates the group without blocking this thread: maxSenderThreads (4) run directly and
            // maxQueueLength (4) more sit in the queue. Under the "none" discard policy, submitting one more than
            // this would block THIS thread inside MaxQueueLengthHandler until a queue slot frees - which cannot
            // happen before releaseSend() below runs, so this thread would deadlock itself rather than exercising
            // the pool's recovery.
            for (int i = 0; i < 8; i++) {
                sender.send(new LogMessage(), "session-1");
            }

            // Wait for the outage to actually be in place before releasing anything: one message must have reached
            // the transport and be parked in its gate (the lucky worker that got the pool's already-connected
            // sender), and at least one connect attempt must have failed (electing the group's single reconnector,
            // which parks every other worker inside SenderPool.acquire()). Without this handshake, the submitting
            // thread could race ahead of every worker thread and disarm the failure before any worker ever hit it,
            // which would pass without ever exercising the outage this test exists to cover.
            assertTrue("a send must reach the transport and block",
                LifecycleTestTransport.sendEnteredLatch().await(10, TimeUnit.SECONDS));
            assertTrue("a connect attempt must fail to elect the group's reconnector",
                LifecycleTestTransport.awaitConnectAttempts(2, 10, TimeUnit.SECONDS));

            LifecycleTestTransport.releaseSend();

            // Now let the connection come back and assert the pool actually drains.
            LifecycleTestTransport.disarmSendFailure();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
            assertTrue("the pool must make progress once reconnected",
                LifecycleTestTransport.awaitSuccessfulSends(1, 30, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }
    }
}
