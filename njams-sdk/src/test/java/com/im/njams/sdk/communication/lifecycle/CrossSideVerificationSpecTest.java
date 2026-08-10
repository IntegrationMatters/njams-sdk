package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;

/**
 * Proves the D1.7 cross-side connection-verification trigger end-to-end through a real {@code Njams}: a
 * connection failure detected on one side (sender or receiver) prompts the other to cycle its own connection,
 * even though the two sides otherwise keep fully independent lifecycle state (see
 * {@code SenderReceiverIndependenceSpecTest}-equivalent coverage already established across Tasks 2-4's own
 * tests — this class covers only the trigger itself).
 */
public class CrossSideVerificationSpecTest extends AbstractLifecycleSpecTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void receiverFailureTriggersTheSenderToCycleItsConnection() throws Exception {
        njams = new Njams(Path.of("test", "crossVerifyReceiverToSender"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);

        // Sender is currently connected and idle. A receiver-side failure must prompt the sender to cycle even
        // though the sender itself observed nothing wrong.
        CountDownLatch senderConnectAttempted = LifecycleTestTransport.connectAttemptedLatch();
        receiver.onException(new IllegalStateException("receiver connection lost"));

        assertTrue("a receiver failure must trigger the sender to cycle (re-attempt) its own connection",
            senderConnectAttempted.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void senderFailureTriggersTheReceiverToCycleItsConnection() throws Exception {
        njams = new Njams(Path.of("test", "crossVerifySenderToReceiver"), "1.0", "test",
            LifecycleTestTransport.settings());
        assertTrue(njams.start());

        LifecycleTestReceiver receiver = LifecycleTestReceiver.lastCreated();
        assertNotNull(receiver);
        receiver.forceDisconnect(); // so the receiver's own subsequent connect() attempt is meaningful, not a no-op

        // Sender is connected; force it to report a failure via its own real onException path.
        LifecycleTestSender sender = LifecycleTestSender.lastCreated();
        assertNotNull("a real LifecycleTestSender must have been pooled by Njams.start()", sender);
        sender.forceDisconnect();

        CountDownLatch receiverConnectAttempted = LifecycleTestTransport.receiverConnectAttemptedLatch();
        sender.onExceptionForTest(new IllegalStateException("sender connection lost"));

        assertTrue("a sender failure must trigger the receiver to cycle (re-attempt) its own connection",
            receiverConnectAttempted.await(2, TimeUnit.SECONDS));
    }
}
