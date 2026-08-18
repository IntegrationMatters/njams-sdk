package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies SDK-473's evidence gate: the pool records whether the failure that opened an outage indicated a
 * broken connection, and only signals recovery when that classification and a genuinely failed reconnect attempt
 * agree.
 */
public class SenderRecoverySignalSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void anUnclassifiedOutageCountsAsABrokenConnection() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("boom"));
        assertTrue("a transport that cannot classify must leave the outage counted as a broken connection",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void anOutageTheSenderRuledOutIsRecordedAsSuch() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newSenderRulingOutConnectionLoss(), new IllegalStateException("queue full"));
        assertFalse("a transport that ruled out a connection loss must be recorded as such",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void aStartupTimeoutCountsAsABrokenConnection() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.restartConnectInBackground(1);
        assertTrue("a startup timeout has no sender to ask and must count as a broken connection",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void aSecondFailureDuringTheSameOutageDoesNotRewriteTheClassification() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        // The group is already reconnecting; a later report belongs to the same outage and must not re-classify it.
        pool.reportFailure(pool.newSenderRulingOutConnectionLoss(), new IllegalStateException("queue full"));
        assertTrue("only the electing failure classifies the outage",
            pool.outageIndicatesBrokenConnection());
    }

    @Test
    public void aReconnectThatSucceedsImmediatelyIsNotRecordedAsAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        // Connect mode stays SUCCEED: the outage flips the group, but the very first reconnect attempt works —
        // the transient-failure-against-a-reachable-endpoint case.
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("one bad send"));
        assertTrue("the group must recover", pool.awaitRecovered(10, TimeUnit.SECONDS));
        assertFalse("a reconnect that succeeded on its first attempt is no evidence of unreachability",
            pool.recoveredAfterFailedConnectAttempt());
    }

    @Test
    public void aReconnectThatHadToRetryIsRecordedAsAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue("the reconnect loop must have attempted at least one connect",
            LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue("the group must recover once connects succeed again", pool.awaitRecovered(10, TimeUnit.SECONDS));
        assertTrue("a reconnect that had to retry is evidence the endpoint was unreachable",
            pool.recoveredAfterFailedConnectAttempt());
    }

    @Test
    public void aStartupConnectIsNeverRecordedAsRecoveryFromAFailedConnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        assertTrue("the startup connect must succeed", pool.awaitStartup(10_000));
        assertFalse("a startup connect follows no outage at all",
            pool.recoveredAfterFailedConnectAttempt());
    }
}
