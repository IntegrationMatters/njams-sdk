package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Specifies the SDK-473/SDK-474 failure-classification seam: a sender says whether a reported failure is mere
 * congestion, and the default must keep the pre-SDK-473 behaviour of assuming a real connection problem.
 */
public class SenderFailureClassificationTest {

    /** Minimal concrete sender that does not override the classification. */
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

        /** Exposes the protected classification to this test. */
        boolean classify(Throwable failure) {
            return isCongestion(failure);
        }
    }

    /** A transport that can identify a load peak as congestion, the way SDK-474 will. */
    private static class ClassifyingSender extends DefaultSender {
        @Override
        protected boolean isCongestion(Throwable failure) {
            return failure instanceof IllegalStateException;
        }
    }

    @Test
    public void defaultClassificationAssumesTheConnectionIsBroken() {
        DefaultSender sender = new DefaultSender();
        assertFalse("a transport that cannot classify must assume a broken connection, not congestion",
            sender.classify(new RuntimeException("boom")));
        assertFalse("a null cause must be treated the same as any unclassifiable failure",
            sender.classify(null));
    }

    @Test
    public void anOverridingTransportCanRuleOutAConnectionLoss() {
        ClassifyingSender sender = new ClassifyingSender();
        assertTrue("an overriding transport must be able to identify congestion",
            sender.classify(new IllegalStateException("queue full")));
        assertFalse("an unrecognised failure must still count as a broken connection",
            sender.classify(new RuntimeException("socket closed")));
    }
}
