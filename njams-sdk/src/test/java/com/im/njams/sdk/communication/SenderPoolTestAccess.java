package com.im.njams.sdk.communication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.lifecycle.LifecycleTestSender;
import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Test bridge exposing the package-private {@link SenderPool} surface to tests in other packages.
 * Thin by design: it adds no logic, only visibility.
 * <p>
 * It deliberately installs no {@code DiscardMonitor}/{@code ThrottleMonitor}: the shared harness
 * ({@code AbstractLifecycleSpecTest}) owns that lifecycle, and a second install would silently replace the
 * harness's instance so one of the two references would count nothing.
 */
public class SenderPoolTestAccess {

    /**
     * Every pool a test created. Teardown must shut these down through the <em>pool</em>, because the pool's
     * {@code ConnectionCoordinator} is unreachable from {@code LifecycleTestTransport.shutdownAllSenders()}: that
     * works through sender instances, and a pool whose reconnect loop has not run its first iteration yet owns no
     * sender at all. Without this, such a loop starts after the test finished and attempts a connect inside the
     * <em>next</em> test's window, counting down that test's shared latch.
     */
    private static final List<SenderPool> POOLS = new CopyOnWriteArrayList<>();

    /**
     * A sender that rules out a connection loss for every failure, standing in for the per-transport
     * classification SDK-474 will implement.
     */
    public static class SenderRulingOutConnectionLoss extends LifecycleTestSender {
        @Override
        protected boolean isConnectionBroken(Throwable failure) {
            return false;
        }
    }

    private final SenderPool pool;

    private SenderPoolTestAccess(SenderPool pool) {
        this.pool = pool;
    }

    /**
     * Stops every pool a test created — cancels its reconnect loop, flags the group as shutting down, and closes
     * its senders — then clears the registry. Called from the shared lifecycle teardown.
     */
    public static void shutdownAll() {
        for (SenderPool p : POOLS) {
            p.beginShutdown();
            p.declareShutdown();
            p.shutdown();
        }
        POOLS.clear();
    }

    /**
     * @param discardPolicy the value for {@code njams.sdk.discardpolicy} ("none", "discard", "onconnectionloss").
     * @return a pool over the LIFECYCLE_TEST transport, with reconnect-before-connected allowed so a
     *         reconnect can be exercised without first completing a real startup connect.
     */
    public static SenderPoolTestAccess create(String discardPolicy) {
        Settings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        ClientSettings settings = ClientSettings.from(s.getAllProperties());
        SenderPool pool = new SenderPool(new CommunicationFactory(settings), new ConnectionCoordinator(), settings);
        pool.allowReconnectBeforeConnected();
        POOLS.add(pool);
        return new SenderPoolTestAccess(pool);
    }

    public Object acquire() {
        return pool.acquire();
    }

    public void release(Object sender) {
        pool.release((AbstractSender) sender);
    }

    public void reportFailure(Object sender, Exception cause) {
        pool.reportFailure((AbstractSender) sender, cause);
    }

    public void beginShutdown() {
        pool.beginShutdown();
    }

    public boolean isConnectionFailure() {
        return pool.isConnectionFailure();
    }

    public void addExceptionListener(SenderExceptionListener listener) {
        pool.addSenderExceptionListener(listener);
    }

    public boolean isConnected(Object sender) {
        return ((AbstractSender) sender).isConnected();
    }

    /** @return a sender that was never connected, for driving {@link SenderPool#reportFailure} directly. */
    public Object newUnconnectedSender() {
        return new LifecycleTestSender();
    }

    /** @return a sender whose classification rules out a connection loss, for driving the evidence gate. */
    public Object newSenderRulingOutConnectionLoss() {
        return new SenderRulingOutConnectionLoss();
    }

    /** @return whether the failure that opened the current outage was classified as a broken connection. */
    public boolean outageIndicatesBrokenConnection() {
        return pool.outageIndicatesBrokenConnectionForTest();
    }

    public void restartConnectInBackground(long timeoutMs) {
        pool.restartConnectInBackground(timeoutMs);
    }

    /**
     * Drives the group into the {@code reconnecting} state through the real code path: the transport is set to
     * BLOCK so the elected reconnect loop parks inside {@code connect()}, leaving the pool genuinely
     * reconnecting rather than having a flag poked.
     */
    public void forceReconnecting() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.BLOCK);
        pool.reportFailure((AbstractSender) newUnconnectedSender(), new IllegalStateException("test-induced loss"));
    }

    /** Completes the blocked reconnect and returns the sender the connector published to the pool. */
    public Object publishConnectedSender() {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        LifecycleTestTransport.releaseBlockedConnect();
        return pool.awaitPublishedSenderForTest();
    }

    public boolean awaitConnectionFailure(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (pool.isConnectionFailure()) {
                return true;
            }
            Thread.sleep(25);
        }
        return pool.isConnectionFailure();
    }

    public int exceptionListenerFireCount() {
        return pool.exceptionListenerFireCountForTest();
    }

    public boolean isRetired(Object sender) {
        return pool.isRetiredForTest((AbstractSender) sender);
    }

    public boolean wasClosed(Object sender) {
        return ((LifecycleTestSender) sender).wasClosed();
    }

    public void notifyConnectionFailureOn(Object sender, Exception cause) {
        ((LifecycleTestSender) sender).reportAsyncFailure(cause);
    }

    /** @return whether the last publish to the pool followed at least one failed connect attempt. */
    public boolean recoveredAfterFailedConnectAttempt() {
        return pool.recoveredAfterFailedConnectAttemptForTest();
    }

    /** Polls until the group is connected again after an outage. */
    public boolean awaitRecovered(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (!pool.isConnectionFailure()) {
                return true;
            }
            Thread.sleep(25);
        }
        return !pool.isConnectionFailure();
    }

    public boolean awaitStartup(long timeoutMs) {
        pool.beginConnect();
        return pool.awaitStartup(timeoutMs);
    }

    public void addRecoveryListener(SenderRecoveryListener listener) {
        pool.addSenderRecoveryListener(listener);
    }

    public void removeRecoveryListener(SenderRecoveryListener listener) {
        pool.removeSenderRecoveryListener(listener);
    }
}
