package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies the pool's borrow/return/failure surface: {@code acquire()} hands out only connected senders and
 * applies the group's discard policy while a reconnect is in progress, {@code release(...)} recycles or retires,
 * and {@code reportFailure(...)} elects exactly one reconnector per outage.
 */
public class SenderPoolAcquireSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void acquireReturnsAConnectedSender() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object sender = pool.acquire();
        assertNotNull("acquire must hand out a sender", sender);
        assertTrue("acquire must hand out a CONNECTED sender", pool.isConnected(sender));
    }

    @Test
    public void exactlyOneReconnectorIsElectedUnderConcurrentFailures() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        int failures = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(failures);
        for (int i = 0; i < failures; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("boom"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals("only the elected failure fires the listeners", 1, pool.exceptionListenerFireCount());
        assertTrue("the group is flagged as failed", pool.isConnectionFailure());
    }

    @Test
    public void acquireDiscardsWhileReconnectingUnderDiscardPolicy() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("discard");
        pool.forceReconnecting();
        assertNull("acquire must give up under DISCARD while reconnecting", pool.acquire());
        // discardMonitor is installed fresh by AbstractLifecycleSpecTest, so this count is absolute.
        assertEquals("exactly one message counted as discarded", 1, discardMonitor.count());
    }

    @Test
    public void acquireDiscardsWhileReconnectingUnderOnConnectionLoss() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("onconnectionloss");
        pool.forceReconnecting();
        assertNull("acquire must give up under ON_CONNECTION_LOSS while reconnecting", pool.acquire());
        assertEquals("exactly one message counted as discarded", 1, discardMonitor.count());
    }

    @Test
    public void acquireDoesNotDiscardWhileTheGroupIsHealthy() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("discard");
        assertNotNull("a healthy group hands out a sender", pool.acquire());
        assertEquals("nothing may be discarded while healthy", 0, discardMonitor.count());
    }

    @Test
    public void acquireBlocksUnderNoneAndIsReleasedByReconnect() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.forceReconnecting();
        AtomicReference<Object> acquired = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            acquired.set(pool.acquire());
            returned.countDown();
        });
        t.setDaemon(true);
        t.start();
        assertNull("acquire must not return while the group reconnects", acquired.get());
        assertEquals("still parked", 1, returned.getCount());
        Object connected = pool.publishConnectedSender();
        assertTrue("the waiter must wake once a sender is published", returned.await(5, TimeUnit.SECONDS));
        assertSame("the waiter gets the reconnected sender", connected, acquired.get());
    }

    @Test
    public void acquireReturnsNullPromptlyOnShutdown() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.forceReconnecting();
        AtomicReference<Object> acquired = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            acquired.set(pool.acquire());
            returned.countDown();
        });
        t.setDaemon(true);
        t.start();
        assertEquals(1, returned.getCount());
        pool.beginShutdown();
        assertTrue("shutdown must wake blocked acquirers", returned.await(5, TimeUnit.SECONDS));
        assertNull("a woken acquirer gets null on shutdown", acquired.get());
        assertEquals("a shutdown-induced drop is not a policy discard", 0, discardMonitor.count());
    }

    /**
     * beginShutdown() only releases callers that would have to <em>wait</em> for a cancelled reconnect. A healthy
     * group must keep serving, or the in-flight sends the executor is still draining would all be dropped.
     */
    @Test
    public void acquireStillServesAHealthyGroupWhileDraining() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.beginShutdown();
        Object sender = pool.acquire();
        assertNotNull("a draining but healthy group must still hand out a sender", sender);
        assertTrue("and it must still be a CONNECTED one", pool.isConnected(sender));
    }

    /**
     * The reconnecting/failed state is latched under the lock and only a published reconnect clears it, so a
     * listener that throws must not be able to prevent the reconnect from starting — that would leave the group
     * permanently parked or discarding — nor prevent the remaining listeners from being notified.
     */
    @Test
    public void aThrowingListenerNeitherBlocksTheReconnectNorTheOtherListeners() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        AtomicInteger healthyListenerCalls = new AtomicInteger();
        pool.addExceptionListener((e, msg) -> {
            throw new IllegalStateException("this listener is broken");
        });
        pool.addExceptionListener((e, msg) -> healthyListenerCalls.incrementAndGet());

        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("boom"));

        assertNotNull("a broken listener must not prevent the reconnect from starting",
            pool.publishConnectedSender());
        assertEquals("the remaining listener must still be notified", 1, healthyListenerCalls.get());
        assertNotNull("and the group must be usable again", pool.acquire());
    }

    @Test
    public void releaseReturnsAHealthySenderToThePool() {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object s1 = pool.acquire();
        pool.release(s1);
        assertSame("a released healthy sender is reused", s1, pool.acquire());
    }
}
