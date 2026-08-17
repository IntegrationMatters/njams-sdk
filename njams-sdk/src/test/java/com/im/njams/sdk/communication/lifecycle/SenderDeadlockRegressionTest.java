package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.MaxQueueLengthHandler;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.SenderPool;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Regression guard for design-spec section 7: the pre-SDK-472 approach of re-enqueueing a failed message back onto
 * the executor could deadlock permanently once the queue is full and <em>every</em> worker is simultaneously trying
 * to do the same thing during an outage — "once every worker does so, nobody is left to drain the queue."
 * <p>
 * Reproducing that requires several senders to be genuinely connected and concurrently in use <em>before</em> the
 * outage hits. A worker whose own connect attempt fails inside {@link SenderPool#acquire()} never reaches
 * {@link NjamsSender}'s retry/re-enqueue branch at all — it just parks in the pool waiting for the reconnect,
 * independent of what {@code dispatch} does on a send failure. Only a worker that fails a real, in-flight
 * {@code send()} call exercises that branch. So this test first warms the pool with {@code maxSenderThreads}
 * distinct connected senders (forcing them all in flight at once via the block-then-succeed gate, so none can
 * finish and be reused by another before all have been created), then arms failure and re-submits exactly that many
 * messages so every one of them is picked up from the now-idle pool (no new connects needed) and blocks on the
 * shared send gate at once, then fills the queue to capacity, then releases the gate so all of them fail
 * simultaneously.
 * <p>
 * Under the current {@link NjamsSender#dispatch} design, a failed send is retained and retried on the same worker
 * thread via a freshly acquired sender — it never touches the executor again. The old design instead re-submitted
 * the message via {@code executor.execute(...)}; with the queue already full and no thread free to drain it (every
 * worker doing the same re-submission at once), that resubmission would block forever under the {@code none}
 * discard policy — the exact permanent deadlock this test guards against. Give it a hard timeout so a regression
 * fails fast in CI instead of hanging it.
 */
public class SenderDeadlockRegressionTest extends AbstractLifecycleSpecTest {

    private static final int MAX_SENDER_THREADS = 4;
    private static final int MAX_QUEUE_LENGTH = 4;

    /** Fails rather than hanging CI if the pool cannot make progress after a reconnect. */
    @Test(timeout = 60_000)
    public void theGroupRecoversWithAFullQueueAndEveryWorkerFailingConcurrently() throws Exception {
        Settings s = LifecycleTestTransport.settings();
        // min == max: guarantees maxSenderThreads distinct worker threads are created the moment the warm-up
        // batch is submitted, rather than some of them queueing behind fewer core threads.
        s.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, String.valueOf(MAX_SENDER_THREADS));
        s.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, String.valueOf(MAX_SENDER_THREADS));
        s.put(NjamsSettings.PROPERTY_MAX_QUEUE_LENGTH, String.valueOf(MAX_QUEUE_LENGTH));
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        try {
            // --- Warm-up: force maxSenderThreads distinct, genuinely concurrent sends -----------------------
            // Each blocks on the gate before any of them can release and be reused, so every one independently
            // acquires/creates its own connected sender. senderMode stays at the default SUCCEED throughout.
            LifecycleTestTransport.armSendBlocksThenSucceeds();
            for (int i = 0; i < MAX_SENDER_THREADS; i++) {
                sender.send(new LogMessage(), "session-warmup");
            }
            assertTrue("all warm-up sends must be genuinely in flight at once",
                LifecycleTestTransport.awaitBlockedSends(MAX_SENDER_THREADS, 10, TimeUnit.SECONDS));
            LifecycleTestTransport.releaseSend();
            assertTrue("the pool must warm up with " + MAX_SENDER_THREADS + " distinct connected senders",
                LifecycleTestTransport.awaitSuccessfulSends(MAX_SENDER_THREADS, 10, TimeUnit.SECONDS));
            LifecycleTestTransport.disarmSendSuccessBlock();
            LifecycleTestTransport.rearmSendGate();

            // --- Outage: every warmed sender fails a real send at once --------------------------------------
            // The pool now holds maxSenderThreads idle, already-connected senders, so all of the next batch is
            // picked up via takePooled() - no connect() call involved - and every worker blocks on a real,
            // in-flight send() concurrently.
            LifecycleTestTransport.armSendBlocksThenFails();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
            for (int i = 0; i < MAX_SENDER_THREADS; i++) {
                sender.send(new LogMessage(), "session-outage");
            }
            assertTrue("every worker must be blocked on a real, in-flight send at once",
                LifecycleTestTransport.awaitBlockedSends(MAX_SENDER_THREADS, 10, TimeUnit.SECONDS));

            // Fill the queue to capacity: every worker is busy on the gate, so these can only queue, never run.
            // Submitting more than maxQueueLength here would block this thread the same way — exactly the
            // capacity this test intentionally saturates without exceeding.
            for (int i = 0; i < MAX_QUEUE_LENGTH; i++) {
                sender.send(new LogMessage(), "session-backlog");
            }

            // Release: every worker fails its send at once. Each retains its message and retries via a freshly
            // acquired sender; since the group is still failing (FAIL mode), every one of them ends up parked in
            // SenderPool.acquire() waiting for the reconnect, never touching the (already full) executor queue
            // again. This is exactly the state the naive re-enqueue design could never recover from: every worker
            // re-submitting into an already-full queue with nobody free to drain it.
            LifecycleTestTransport.releaseSend();

            // --- Recovery: the connection returns and the pool must drain the whole backlog -----------------
            LifecycleTestTransport.disarmSendFailure();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
            int totalMessages = MAX_SENDER_THREADS + MAX_SENDER_THREADS + MAX_QUEUE_LENGTH;
            assertTrue("the pool must drain the whole backlog once reconnected",
                LifecycleTestTransport.awaitSuccessfulSends(totalMessages, 30, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }
    }
}
