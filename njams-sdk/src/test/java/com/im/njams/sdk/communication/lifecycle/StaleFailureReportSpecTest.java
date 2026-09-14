package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies that a failure reported about a sender the group already retired is absorbed rather than treated as a
 * new outage. A slow thread can finish retrying long after its connection was replaced; its verdict is evidence
 * about a connection generation that no longer exists.
 */
public class StaleFailureReportSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void aFailureReportedAfterRecoveryDoesNotOpenASecondOutage() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object stale = pool.acquire();          // held by the thread that is still retrying

        // Another sender fails: one reconnector is elected and `stale` is retired underneath its borrower.
        pool.forceReconnecting();
        assertTrue("the checked-out sender must be retired by the group failure", pool.isRetired(stale));
        assertEquals("one outage, one notification", 1, pool.exceptionListenerFireCount());

        // The reconnect completes and publishes a healthy sender.
        Object recovered = pool.publishConnectedSender();
        assertNotNull("the connector must publish a sender", recovered);
        assertTrue("the group must be healthy again", pool.awaitRecovered(5, TimeUnit.SECONDS));

        // Only now does the slow thread report the failure it ended on.
        pool.reportFailure(stale, new IllegalStateException("stale failure from the replaced connection"));

        assertEquals("a stale report must not elect a second reconnector or re-notify listeners",
            1, pool.exceptionListenerFireCount());
        assertFalse("the group must stay healthy", pool.isConnectionFailure());
        assertFalse("the freshly published sender must not be destroyed by a stale report",
            pool.wasClosed(recovered));
        assertTrue("the stale sender itself is still closed", pool.wasClosed(stale));
    }

    @Test
    public void aFailureOnACurrentSenderStillOpensAnOutage() throws Exception {
        // Contrast case: the guard must absorb only stale reports, never a real one.
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object current = pool.acquire();

        pool.reportFailure(current, new IllegalStateException("genuine connection loss"));

        assertEquals("a failure on a current sender must elect a reconnector", 1,
            pool.exceptionListenerFireCount());
        assertTrue(pool.awaitConnectionFailure(5, TimeUnit.SECONDS));
    }
}
