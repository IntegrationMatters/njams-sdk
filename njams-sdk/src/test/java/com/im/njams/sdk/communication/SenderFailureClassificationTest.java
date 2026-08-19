package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Specifies the SDK-473/SDK-474/SDK-474-followup failure-classification seam: both hooks are abstract, so every
 * sender must decide them for itself — there is no inherited default. This specifies the documented safe answer
 * (assume a real connection problem / assume retrying might still help) for a transport that implements the
 * hooks conservatively because it cannot positively classify a failure, and confirms an overriding transport can
 * still rule either classification in.
 */
public class SenderFailureClassificationTest {

    /** Minimal concrete sender implementing both hooks with the documented safe default. */
    private static class DefaultSender extends AbstractSender {
        @Override
        public String getName() {
            return "classification-default";
        }

        @Override
        public void connect() {
            // no transport
        }

        @Override
        public void close() {
            // no transport
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
            return false;
        }

        @Override
        protected boolean isMessageRejected(Throwable failure) {
            return false;
        }

        /** Exposes the protected congestion classification to this test. */
        boolean classifyCongestion(Throwable failure) {
            return isCongestion(failure);
        }

        /** Exposes the protected message-rejection classification to this test. */
        boolean classifyMessageRejected(Throwable failure) {
            return isMessageRejected(failure);
        }
    }

    /** A transport that can identify a load peak as congestion and a payload as permanently rejected. */
    private static class ClassifyingSender extends DefaultSender {
        @Override
        protected boolean isCongestion(Throwable failure) {
            return failure instanceof IllegalStateException;
        }

        @Override
        protected boolean isMessageRejected(Throwable failure) {
            return failure instanceof IllegalArgumentException;
        }
    }

    @Test
    public void safeDefaultClassificationAssumesTheConnectionIsBroken() {
        DefaultSender sender = new DefaultSender();
        assertFalse("a transport that cannot classify must assume a broken connection, not congestion",
            sender.classifyCongestion(new RuntimeException("boom")));
        assertFalse("a null cause must be treated the same as any unclassifiable failure",
            sender.classifyCongestion(null));
    }

    @Test
    public void safeDefaultClassificationAssumesRetryingMightStillHelp() {
        DefaultSender sender = new DefaultSender();
        assertFalse("a transport that cannot classify must assume retrying might still help",
            sender.classifyMessageRejected(new RuntimeException("boom")));
        assertFalse("a null cause must be treated the same as any unclassifiable failure",
            sender.classifyMessageRejected(null));
    }

    @Test
    public void anOverridingTransportCanRuleOutAConnectionLoss() {
        ClassifyingSender sender = new ClassifyingSender();
        assertTrue("an overriding transport must be able to identify congestion",
            sender.classifyCongestion(new IllegalStateException("queue full")));
        assertFalse("an unrecognised failure must still count as a broken connection",
            sender.classifyCongestion(new RuntimeException("socket closed")));
    }

    @Test
    public void anOverridingTransportCanIdentifyAPermanentRejection() {
        ClassifyingSender sender = new ClassifyingSender();
        assertTrue("an overriding transport must be able to identify a permanent rejection",
            sender.classifyMessageRejected(new IllegalArgumentException("payload too large")));
        assertFalse("an unrecognised failure must still count as possibly retryable",
            sender.classifyMessageRejected(new RuntimeException("socket closed")));
    }
}
