package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies which failures make the dispatching loop retire a sender and which only drop the message. A
 * connection is only ever retired for a failure the sender could not attribute to congestion or to the message
 * itself. See docs/superpowers/specs/2026-09-09-sdk-476-sender-retry-unification-discussion.md.
 * <p>
 * Retirement is observed through the sender-exception listener, which only ever fires via
 * SenderPool.reportFailure. The absence checks are sound rather than racy: once the discard has been counted the
 * dispatching task for that message has finished, and no other code path can report a failure for it afterwards.
 */
public class SenderDispatchClassificationTest {

    @After
    public void afterEach() {
        CountingDiscardMonitor.restore();
        TestSender.setSenderMock(null);
    }

    private static NjamsSender senderWithPolicy(String policy) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return new NjamsSender(settings);
    }

    private static LogMessage logMessage() {
        LogMessage msg = new LogMessage();
        msg.setLogId("log-1");
        msg.setPath(">a>b>");
        return msg;
    }

    @Test
    public void aRejectedMessageIsDroppedWithoutRetiringTheSender() throws Exception {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.REJECTED));
        NjamsSender sender = senderWithPolicy("none");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue("the rejected message must be counted as discarded",
                monitor.awaitAtLeast(1, 5, TimeUnit.SECONDS));
            assertFalse("a rejected message must not retire the connection", retired.get());
            assertEquals("exactly one discard for one dropped message", 1, monitor.count());
        } finally {
            sender.close();
        }
    }

    @Test
    public void aCongestionFailureIsDroppedWithoutRetiringTheSender() throws Exception {
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.CONGESTION));
        // Only the discard policy lets congestion reach dispatch at all; see the invariant in Task 5.
        NjamsSender sender = senderWithPolicy("discard");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue(monitor.awaitAtLeast(1, 5, TimeUnit.SECONDS));
            assertFalse("congestion must never retire the connection", retired.get());
        } finally {
            sender.close();
        }
    }

    @Test
    public void anUnclassifiedFailureRetiresTheSenderAndReportsTheOutage() throws Exception {
        CountingDiscardMonitor.install();
        TestSender.setSenderMock(new ClassifyingFailureSender(ClassifyingFailureSender.Kind.OTHER));
        NjamsSender sender = senderWithPolicy("discard");
        try {
            AtomicBoolean retired = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> retired.set(true));
            sender.send(logMessage(), "session-1");
            long deadline = System.currentTimeMillis() + 5000;
            while (!retired.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertTrue("a real connection problem must be reported to the pool's listeners", retired.get());
        } finally {
            sender.close();
        }
    }

    @Test
    public void aSenderRetiredMidSendHandsTheMessageToAFreshSenderInsteadOfReportingAnOutage() throws Exception {
        // Race B, dispatch's half: the retirement signal is not evidence about the current connection, so it must
        // neither discard the message nor open an outage - it must put the same message on a current sender.
        CountingDiscardMonitor monitor = CountingDiscardMonitor.install();
        RetiredThenSucceedingSender delegate = new RetiredThenSucceedingSender();
        TestSender.setSenderMock(delegate);
        NjamsSender sender = senderWithPolicy("none");
        try {
            AtomicBoolean outageReported = new AtomicBoolean();
            sender.addSenderExceptionListener((exception, msg) -> outageReported.set(true));
            sender.send(logMessage(), "session-1");
            assertTrue("the message must be re-sent and succeed",
                delegate.awaitSuccessfulSend(5, TimeUnit.SECONDS));
            assertFalse("a mid-send retirement is not an outage", outageReported.get());
            assertEquals("the message must not be discarded", 0, monitor.count());
            assertEquals("exactly one retry on a fresh sender", 2, delegate.sendCount());
        } finally {
            sender.close();
        }
    }
}
