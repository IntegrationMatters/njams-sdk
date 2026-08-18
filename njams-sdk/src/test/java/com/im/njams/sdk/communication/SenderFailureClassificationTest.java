package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;

/**
 * Specifies the SDK-473/SDK-474 failure-classification seam: a sender says whether a reported failure indicates a
 * broken connection, and the default must keep the pre-SDK-473 behaviour of assuming it does.
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
            return isConnectionBroken(failure);
        }
    }

    /** A transport that can rule a load peak out, the way SDK-474 will. */
    private static class ClassifyingSender extends DefaultSender {
        @Override
        protected boolean isConnectionBroken(Throwable failure) {
            return !(failure instanceof IllegalStateException);
        }
    }

    @Test
    public void defaultClassificationAssumesTheConnectionIsBroken() {
        DefaultSender sender = new DefaultSender();
        assertTrue("a transport that cannot classify must assume a broken connection",
            sender.classify(new RuntimeException("boom")));
        assertTrue("a null cause must be treated the same as any unclassifiable failure",
            sender.classify(null));
    }

    @Test
    public void anOverridingTransportCanRuleOutAConnectionLoss() {
        ClassifyingSender sender = new ClassifyingSender();
        assertFalse("an overriding transport must be able to rule out a connection loss",
            sender.classify(new IllegalStateException("queue full")));
        assertTrue("an unrecognised failure must still count as a broken connection",
            sender.classify(new RuntimeException("socket closed")));
    }
}
