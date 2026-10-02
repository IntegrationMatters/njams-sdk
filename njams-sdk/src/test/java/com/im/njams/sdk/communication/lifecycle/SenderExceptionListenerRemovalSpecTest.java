package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.SenderExceptionListener;
import com.im.njams.sdk.communication.SenderPoolTestAccess;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Specifies the removal counterpart of {@code addSenderExceptionListener} (SDK-485): a sender group outlives the
 * clients using it, so a client must be able to take its exception listener with it. Mirrors the recovery-listener
 * removal spec in {@link SenderRecoverySignalSpecTest}.
 */
public class SenderExceptionListenerRemovalSpecTest extends AbstractLifecycleSpecTest {

    /** Counts notifications reaching this listener. */
    private static final class CountingListener implements SenderExceptionListener {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void onException(Exception exception, CommonMessage msg) {
            count.incrementAndGet();
        }
    }

    private NjamsSender shared;

    @After
    public void releaseSharedSender() {
        if (shared != null) {
            shared.close();
            shared = null;
        }
    }

    private static void causeOutage(SenderPoolTestAccess pool) throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new RuntimeException("real loss"));
        assertTrue(LifecycleTestTransport.awaitConnectAttempts(1, 10, TimeUnit.SECONDS));
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        assertTrue(pool.awaitRecovered(10, TimeUnit.SECONDS));
    }

    @Test
    public void aRemovedListenerIsNotNotified() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingListener listener = new CountingListener();
        pool.addExceptionListener(listener);
        pool.removeExceptionListener(listener);

        causeOutage(pool);

        assertEquals("a deregistered listener must never be notified again", 0, listener.count.get());
        assertEquals(0, pool.exceptionListenerCount());
    }

    @Test
    public void removingOneListenerLeavesTheOthersRegisteredAndNotified() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingListener removed = new CountingListener();
        CountingListener kept = new CountingListener();
        pool.addExceptionListener(removed);
        pool.addExceptionListener(kept);
        pool.removeExceptionListener(removed);

        causeOutage(pool);

        assertEquals(0, removed.count.get());
        assertEquals("a listener that was not removed is still notified once per outage", 1, kept.count.get());
        assertEquals(1, pool.exceptionListenerCount());
    }

    @Test
    public void removingAnUnknownListenerIsIgnored() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingListener registered = new CountingListener();
        pool.addExceptionListener(registered);

        pool.removeExceptionListener(new CountingListener());

        assertEquals(1, pool.exceptionListenerCount());
    }

    @Test
    public void aListenerRegisteredTwiceIsRemovedByOneRemoval() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        CountingListener listener = new CountingListener();
        // Mirrors two Njams instances registering the same shared receiver object.
        pool.addExceptionListener(listener);
        pool.addExceptionListener(listener);
        assertEquals("the listener set has identity semantics", 1, pool.exceptionListenerCount());

        pool.removeExceptionListener(listener);

        assertEquals(0, pool.exceptionListenerCount());
    }

    @Test
    public void theSharedSendersRegistrationsReturnToZeroOnceRemoved() {
        ClientSettings s = LifecycleTestTransport.settings();
        s.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        shared = NjamsSender.takeSharedSender(s);
        CountingListener a = new CountingListener();
        CountingListener b = new CountingListener();
        shared.addSenderExceptionListener(a);
        shared.addSenderExceptionListener(b);
        assertEquals(2, shared.exceptionListenerCount());

        shared.removeSenderExceptionListener(a);
        shared.removeSenderExceptionListener(b);

        assertEquals("registrations must not accumulate on a sender group that outlives its clients", 0,
            shared.exceptionListenerCount());
    }
}
