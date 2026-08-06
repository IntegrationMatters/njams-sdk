package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Regression coverage for SDK-375 final-review findings #3 and #4, exercised against the real (unmodified)
 * {@link SharedJmsReceiver#removeNjams(Njams)} — only {@link #connect()} is overridden, so the receiver never
 * touches a real JMS broker.
 * <p>
 * Finding #3: {@code removeNjams()} used to re-enter this receiver's own monitor (via {@code updateFilters()}/
 * {@code updateConsumer()}) once the last registered instance was removed, without first cancelling an
 * in-progress reconnect — which holds that same monitor for its entire retry loop
 * ({@link com.im.njams.sdk.communication.AbstractReceiver#reconnect(Exception)} is {@code synchronized}). That
 * could block {@code removeNjams()} (and therefore the caller's {@code Njams.stop()}) for the remainder of the
 * reconnect attempt.
 * <p>
 * Finding #4: {@link com.im.njams.sdk.communication.ShareableReceiver#removeNjams(Njams)}'s {@code boolean}
 * return value (changed from {@code void}) had zero test coverage anywhere in the repository.
 */
public class SharedJmsReceiverTest {

    private static Njams mockNjams(String... pathParts) {
        Njams njams = mock(Njams.class);
        when(njams.getClientPath()).thenReturn(Path.of(pathParts));
        return njams;
    }

    /** Overrides {@link #connect()} to block until interrupted, instead of touching a real JMS broker. */
    private static class BlockingSharedJmsReceiver extends SharedJmsReceiver {
        final CountDownLatch connectEntered = new CountDownLatch(1);

        @Override
        public synchronized void connect() {
            connectEntered.countDown();
            try {
                Thread.sleep(10_000); // "blocks" until interrupted by cancelReconnect()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NjamsSdkRuntimeException("interrupted");
            }
            connectionStatus = ConnectionStatus.CONNECTED;
        }
    }

    @Test
    public void removeNjamsOfTheLastInstanceDoesNotBlockOnAnInProgressReconnect() throws Exception {
        BlockingSharedJmsReceiver receiver = new BlockingSharedJmsReceiver();
        Njams only = mockNjams("test", "shared-jms-hang");
        receiver.setNjams(only);

        Thread reconnector = new Thread(() -> receiver.reconnect(new NjamsSdkRuntimeException("lost")));
        reconnector.setDaemon(true);
        reconnector.start();
        assertTrue("reconnect must have entered connect() and blocked",
            receiver.connectEntered.await(2, TimeUnit.SECONDS));

        long before = System.currentTimeMillis();
        boolean reallyStopped = receiver.removeNjams(only);
        long elapsed = System.currentTimeMillis() - before;

        assertTrue("the only registered instance was removed -> really stopped", reallyStopped);
        assertTrue("removeNjams() must not block on the monitor reconnect() holds for its whole retry loop "
            + "(elapsed=" + elapsed + "ms)", elapsed < 2000);

        reconnector.join(2000);
        assertFalse("the reconnect thread must terminate once cancelReconnect() interrupted its blocked connect()",
            reconnector.isAlive());
    }

    @Test
    public void removeNjamsWhileAnotherInstanceStillUsesTheReceiverDoesNotTouchTheInProgressReconnect()
            throws Exception {
        BlockingSharedJmsReceiver receiver = new BlockingSharedJmsReceiver();
        Njams first = mockNjams("test", "shared-jms-a");
        Njams second = mockNjams("test", "shared-jms-b");
        receiver.setNjams(first);
        receiver.setNjams(second);

        Thread reconnector = new Thread(() -> receiver.reconnect(new NjamsSdkRuntimeException("lost")));
        reconnector.setDaemon(true);
        reconnector.start();
        assertTrue("reconnect must have entered connect() and blocked",
            receiver.connectEntered.await(2, TimeUnit.SECONDS));

        boolean reallyStopped = receiver.removeNjams(first);

        assertFalse("a second instance still uses the shared receiver -> not really stopped", reallyStopped);
        assertTrue("removing one of several sharers must not cancel the group's in-progress reconnect",
            reconnector.isAlive());

        // Clean up the still-blocked daemon thread ourselves so it does not outlive this test.
        receiver.cancelReconnect();
        reconnector.join(2000);
    }
}
