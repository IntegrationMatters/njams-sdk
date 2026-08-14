package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Specifies {@code NjamsSender}'s retention loop: a message stays on its worker thread across sender failures and is
 * retried on a freshly acquired sender, unless the group's discard policy gives up on it.
 */
public class MessageRetentionSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void aMessageSurvivesItsSenderBeingRetiredAndIsSentOnce() throws Exception {
        // Retention is what the "none" policy means; under discard/onconnectionloss the message is dropped
        // instead, which the two tests below specify.
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        try {
            // Arm the first send to block, then fail and drop the connection.
            LifecycleTestTransport.armSendBlocksThenFails();
            sender.send(new LogMessage(), "session-1");
            assertTrue("the send must reach the transport",
                LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));

            // Let the send fail; the worker must retain the message and retry on a fresh sender.
            LifecycleTestTransport.disarmSendFailure();
            LifecycleTestTransport.releaseSend();

            assertTrue("the retained message must be delivered by a fresh sender",
                LifecycleTestTransport.awaitSuccessfulSends(1, 10, TimeUnit.SECONDS));
            assertEquals("delivered exactly once", 1, LifecycleTestTransport.successfulSendCount());
        } finally {
            sender.close();
        }
    }

    /**
     * Spec 10: the relocated base-class discard branch must behave through acquire() exactly as it did inside
     * AbstractSender.send - a message dispatched while the group is reconnecting is discarded, not retained.
     */
    @Test
    public void aMessageDispatchedWhileReconnectingIsDiscardedUnderOnConnectionLoss() throws Exception {
        assertMessageDiscardedWhileReconnecting("onconnectionloss");
    }

    @Test
    public void aMessageDispatchedWhileReconnectingIsDiscardedUnderDiscard() throws Exception {
        assertMessageDiscardedWhileReconnecting("discard");
    }

    private void assertMessageDiscardedWhileReconnecting(String discardPolicy) throws Exception {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        NjamsSender sender = new NjamsSender(ClientSettings.from(s.getAllProperties()));
        assertTrue(sender.startWithTimeout(5000));

        try {
            // Drive the group into a reconnect that cannot complete, then dispatch into it.
            LifecycleTestTransport.armSendBlocksThenFails();
            LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
            sender.send(new LogMessage(), "session-1");
            assertTrue(LifecycleTestTransport.sendEnteredLatch().await(5, TimeUnit.SECONDS));
            LifecycleTestTransport.releaseSend();

            // discardMonitor is installed fresh by AbstractLifecycleSpecTest's @Before; do not install another.
            // Waiting for the failing message's own discard is the handshake that the group has really entered the
            // reconnect, so the next message is provably dispatched *into* a reconnecting group.
            assertTrue("the failing message itself must be discarded once the group fails",
                discardMonitor.awaitAtLeast(1, 10, TimeUnit.SECONDS));
            int successesBefore = LifecycleTestTransport.successfulSendCount();
            int discardsBefore = discardMonitor.count();

            sender.send(new LogMessage(), "session-2");

            assertTrue("the dispatched message must be discarded, not retained",
                discardMonitor.awaitAtLeast(discardsBefore + 1, 10, TimeUnit.SECONDS));
            assertEquals("a discarded message must never be delivered", successesBefore,
                LifecycleTestTransport.successfulSendCount());
        } finally {
            sender.close();
        }
    }
}
