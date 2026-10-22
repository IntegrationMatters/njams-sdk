package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.SenderPoolTestAccess;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Regression guard for the suite-wide order/timing nondeterminism found while finishing SDK-494: a reconnect
 * loop that survives its own test keeps retrying every second and, on the first attempt that succeeds, logs
 * "Reconnected sender" into whichever appender the <em>next</em> test has attached to the shared
 * {@code SenderConnector} category. One randomized full-suite run showed 14 such successes inside a ten-second
 * window, from loops belonging to tests that had long finished.
 *
 * <p>The harness teardown must therefore not merely ask the SDK's threads to stop; it must wait until they
 * actually have.
 */
public class LifecycleTeardownQuiescenceSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void teardownLeavesNoSdkSenderThreadBehindForAPoolTheBridgesKnow() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("lost"));
        assertTrue("the reconnect loop must be running for this to prove anything",
            LifecycleTestTransport.awaitConnectAttempts(1, 5, TimeUnit.SECONDS));

        stopLifecycleSendersAndReceivers();

        assertEquals("no SDK sender thread may outlive the test that started it: " + liveSdkSenderThreadNames(),
            0, liveSdkSenderThreadNames().size());
    }

    /**
     * The case the bridges cannot cover: a group owned by a real {@link Njams} that the test never stops. This
     * is how the leaked loops in the failing run arose -- several lifecycle tests construct an {@code Njams}
     * under the {@code reconnect} policy and leave it running, and neither {@code SenderPoolTestAccess} nor
     * {@code LifecycleTestTransport} ever learns of the pool behind it.
     */
    /** The receiver side of the same contract: its background threads must not outlive their test either. */
    @Test
    public void teardownLeavesNoSdkReceiverThreadBehind() throws Exception {
        LifecycleTestReceiver receiver = new LifecycleTestReceiver();
        receiver.beginConnect();
        LifecycleTestTransport.setReceiverMode(LifecycleTestTransport.ConnectMode.BLOCK);
        // Captured before triggering: onReceiverConnect() counts the current latch down and replaces it at once,
        // so one fetched afterwards may be the fresh, uncounted one and would never fire.
        CountDownLatch reconnectEnteredConnect = LifecycleTestTransport.receiverConnectAttemptedLatch();
        receiver.onException(new IllegalStateException("lost"));
        assertTrue("the receiver's reconnect must have entered connect()",
            reconnectEnteredConnect.await(5, TimeUnit.SECONDS));

        stopLifecycleSendersAndReceivers();

        assertEquals("no SDK receiver thread may outlive the test that started it: "
            + liveSdkReceiverThreadNames(), 0, liveSdkReceiverThreadNames().size());
    }

    @Test
    public void teardownLeavesNoSdkSenderThreadBehindForAnUnstoppedNjams() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ClientSettings settings = LifecycleTestTransport.settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "reconnect");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, "200");
        new Njams(Path.of("teardown", "quiescence"), "1.0", "test", settings).startup();
        assertTrue("the reconnect loop must be running for this to prove anything",
            LifecycleTestTransport.awaitConnectAttempts(2, 10, TimeUnit.SECONDS));

        stopLifecycleSendersAndReceivers();

        assertEquals("no SDK sender thread may outlive the test that started it: " + liveSdkSenderThreadNames(),
            0, liveSdkSenderThreadNames().size());
    }

}
