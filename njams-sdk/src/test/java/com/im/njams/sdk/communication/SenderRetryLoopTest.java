package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Specifies the shared retry machinery in {@link AbstractSender}: the bounded smoothing window, the
 * congestion-only indefinite retry, and which failures are propagated to the caller. See
 * docs/superpowers/specs/2026-09-09-sdk-476-sender-retry-unification-discussion.md.
 */
public class SenderRetryLoopTest {

    /** Congestion marker for this test's classifier. */
    private static class Congested extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Permanently-unsendable marker for this test's classifier. */
    private static class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Sender with no transport: counts attempts, fails them on demand, and uses zero delays. */
    private static class RetrySender extends AbstractSender {
        private int attempts = 0;
        private int failuresToProduce = 0;
        private RuntimeException failureToThrow = new RuntimeException("other");
        private final List<SendFailureOutcome> logged = new ArrayList<>();
        private long congestionDelayMs = 0;

        RetrySender(String policy) {
            Properties props = new Properties();
            props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
            init(ClientSettings.from(props));
        }

        @Override
        public String getName() {
            return "retry-loop";
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
            // not exercised
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
            // not exercised
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
            // not exercised
        }

        @Override
        protected boolean isCongestion(Throwable failure) {
            return failure instanceof Congested;
        }

        @Override
        protected boolean isMessageRejected(Throwable failure) {
            return failure instanceof Rejected;
        }

        @Override
        protected long[] getSmoothingDelaysMs() {
            return new long[] { 0, 0, 0 };
        }

        @Override
        protected long getCongestionRetryDelayMs() {
            return congestionDelayMs;
        }

        /** Overrides the default zero delay; only needed by tests that observe the wait itself. */
        void congestionDelayMs(long ms) {
            congestionDelayMs = ms;
        }

        @Override
        protected void logError(SendFailureOutcome outcome, Throwable failure) {
            logged.add(outcome);
        }

        /** Attempt number at which the pool is simulated to retire this sender mid-send; 0 = never. */
        private int retireAtAttempt = 0;

        void retireAtAttempt(int attempt) {
            retireAtAttempt = attempt;
        }

        /** Runs one send through the shared machinery, failing the first {@code failures} attempts. */
        void run(int failures, RuntimeException failure) throws Exception {
            failuresToProduce = failures;
            failureToThrow = failure;
            sendWithRetry(() -> {
                attempts++;
                if (retireAtAttempt > 0 && attempts >= retireAtAttempt) {
                    // Exactly what SenderPool.failGroup does to a sender that is checked out when the group fails.
                    setRetired();
                }
                if (attempts <= failuresToProduce) {
                    throw failureToThrow;
                }
            });
        }
    }

    @Test
    public void aSingleTransientFailureIsSmoothedAwayAndNeverReachesTheCaller() throws Exception {
        RetrySender sender = new RetrySender("none");
        sender.run(1, new RuntimeException("blip"));
        assertEquals("one retry must be enough to absorb a single blip", 2, sender.attempts);
    }

    @Test
    public void theSmoothingWindowIsBoundedToThreeRetries() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected the failure to be propagated once the smoothing window is exhausted");
        } catch (RuntimeException expected) {
            assertEquals("one initial attempt plus three smoothing retries", 4, sender.attempts);
        }
    }

    @Test
    public void aRejectedMessageIsNeverRetried() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new Rejected());
            fail("expected a rejected message to be propagated immediately");
        } catch (Rejected expected) {
            assertEquals("retrying a rejected message can never help", 1, sender.attempts);
        }
    }

    @Test
    public void discardPolicyNeverRetriesAtAll() throws Exception {
        RetrySender sender = new RetrySender("discard");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected the failure to be propagated on the first attempt under discard");
        } catch (RuntimeException expected) {
            assertEquals("discard must not delay the monitored application at all", 1, sender.attempts);
        }
    }

    @Test
    public void congestionIsRetriedIndefinitelyUnderNone() throws Exception {
        RetrySender sender = new RetrySender("none");
        // far more failures than the smoothing window: proves the outer loop keeps re-entering it
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void congestionIsRetriedIndefinitelyUnderOnConnectionLoss() throws Exception {
        RetrySender sender = new RetrySender("onconnectionloss");
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void congestionUnderDiscardIsPropagatedImmediately() throws Exception {
        RetrySender sender = new RetrySender("discard");
        Congested congestion = new Congested();
        try {
            sender.run(Integer.MAX_VALUE, congestion);
            fail("expected congestion to be propagated under the discard policy");
        } catch (Congested expected) {
            assertSame("the original failure must be rethrown, not wrapped", congestion, expected);
            assertEquals(1, sender.attempts);
        }
    }

    @Test
    public void theFinalFailureIsRethrownUnchanged() throws Exception {
        RetrySender sender = new RetrySender("none");
        RuntimeException failure = new RuntimeException("socket closed");
        try {
            sender.run(Integer.MAX_VALUE, failure);
            fail("expected the original failure");
        } catch (RuntimeException expected) {
            assertSame("classification at the catch site depends on the same throwable", failure, expected);
        }
    }

    @Test
    public void escalationIsLoggedThroughTheTransportHook() throws Exception {
        RetrySender sender = new RetrySender("none");
        try {
            sender.run(Integer.MAX_VALUE, new RuntimeException("down"));
            fail("expected escalation");
        } catch (RuntimeException expected) {
            assertEquals(1, sender.logged.stream().filter(o -> o == AbstractSender.SendFailureOutcome.ESCALATING)
                .count());
        }
    }

    // --- Race B: the congestion loop must not ride a connection the group has already replaced ---

    @Test
    public void congestionStopsBeingWaitedOutOnceTheGroupRetiredThisSender() throws Exception {
        // Without this, the outer loop under a never-discard policy would retry forever on a sender the pool has
        // already given up on: the message would never reach the healthy connection and the retired sender would
        // never be closed.
        RetrySender sender = new RetrySender("none");
        sender.retireAtAttempt(1);
        try {
            sender.run(Integer.MAX_VALUE, new Congested());
            fail("expected the retry to unwind once this sender was retired");
        } catch (SenderRetiredException expected) {
            assertEquals("at most one smoothing window, then unwind - not an unbounded loop", 4, sender.attempts);
        }
    }

    @Test
    public void retirementPartWayThroughWaitingOutCongestionStopsTheLoopPromptly() throws Exception {
        RetrySender sender = new RetrySender("onconnectionloss");
        sender.retireAtAttempt(6);
        try {
            sender.run(Integer.MAX_VALUE, new Congested());
            fail("expected the retry to unwind once this sender was retired");
        } catch (SenderRetiredException expected) {
            assertTrue("must unwind within the window it was retired in, not keep going: " + sender.attempts,
                sender.attempts <= 8);
        }
    }

    @Test
    public void aHealthySenderNeverProducesTheRetirementSignal() throws Exception {
        // Contrast case: the abort must be caused by retirement, not merely by congestion persisting.
        RetrySender sender = new RetrySender("none");
        sender.run(50, new Congested());
        assertEquals(51, sender.attempts);
    }

    @Test
    public void theRetirementSignalIsFoundThroughAWrappingException() {
        // JmsSender.send wraps unconditionally (JmsSender.java:192-194), so dispatch can only ever find this
        // signal by walking the cause chain.
        SenderRetiredException signal = new SenderRetiredException(new Congested());
        assertTrue(SenderRetiredException.isIn(signal));
        assertTrue(SenderRetiredException.isIn(
            new NjamsSdkRuntimeException("Unable to send LogMessage", signal)));
        assertFalse(SenderRetiredException.isIn(new RuntimeException("socket closed")));
        assertFalse(SenderRetiredException.isIn(null));
    }

    @Test
    public void congestionNeverEscapesTheOuterLoopExceptUnderDiscard() throws Exception {
        // The invariant the dispatching loop depends on: if a congestion failure escaped under a policy that
        // never discards, the SDK would drop a message those policies promise to keep retrying forever.
        // Retirement is the one other way out, and it does not violate this: it unwinds with
        // SenderRetiredException, which dispatch retries rather than classifying as congestion.
        for (String policy : new String[] { "none", "onconnectionloss" }) {
            RetrySender sender = new RetrySender(policy);
            sender.run(200, new Congested());
            assertEquals("congestion must be retried, not escalated, under " + policy, 201, sender.attempts);
        }
        RetrySender discarding = new RetrySender("discard");
        try {
            discarding.run(Integer.MAX_VALUE, new Congested());
            fail("only the discard policy may let congestion reach the caller");
        } catch (Congested expected) {
            assertEquals(1, discarding.attempts);
        }
    }

    @Test
    public void anUnclassifiedFailureIsStillEscalatedUnderPoliciesThatNeverDiscard() throws Exception {
        // Contrast case: the invariant is specific to congestion, not a blanket "these policies never throw".
        for (String policy : new String[] { "none", "onconnectionloss" }) {
            RetrySender sender = new RetrySender(policy);
            try {
                sender.run(Integer.MAX_VALUE, new RuntimeException("socket closed"));
                fail("a real connection problem must be escalated under " + policy);
            } catch (RuntimeException expected) {
                assertEquals(4, sender.attempts);
            }
        }
    }

    // --- The congestion wait must be visible to operators through the shared throttle monitor ---

    @Test
    public void congestionWaitReportsToThrottleMonitor() throws Exception {
        CountingThrottleMonitor monitor = CountingThrottleMonitor.install();
        try {
            RetrySender sender = new RetrySender("none");
            sender.congestionDelayMs(7);
            // 4 failing attempts exhaust exactly one smoothing window (1 initial + 3 retries), so the outer loop
            // sleeps exactly once before the window resets and the 5th attempt succeeds.
            sender.run(4, new Congested());
            assertEquals("the one congestion wait must report its delay", 7L, monitor.totalMs());
        } finally {
            CountingThrottleMonitor.restore();
        }
    }

    @Test
    public void aSelfReferentialCauseChainDoesNotHangTheDetection() {
        // A custom transport exception whose getCause() returns itself must not spin the runtime path forever.
        RuntimeException looping = new RuntimeException("loop") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(SenderRetiredException.isIn(looping));
    }
}
