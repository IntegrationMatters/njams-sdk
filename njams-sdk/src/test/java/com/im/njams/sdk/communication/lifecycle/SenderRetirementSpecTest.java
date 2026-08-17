package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Specifies retirement (D2.4): a sender that is checked out when the group fails is flagged retired but never
 * closed by the failing thread — it stays with its borrower until release — while an idle sender with nobody
 * holding it is destroyed immediately.
 */
public class SenderRetirementSpecTest extends AbstractLifecycleSpecTest {

    @Test
    public void anInUseSenderIsNotClosedUntilItsBorrowerReleasesIt() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object borrowed = pool.acquire();
        Object other = pool.acquire();

        // A failure on `other` retires `borrowed` without closing it.
        pool.reportFailure(other, new IllegalStateException("group loss"));

        assertFalse("a checked-out sender must not be closed by the failing thread", pool.wasClosed(borrowed));
        assertTrue("but it must be flagged retired", pool.isRetired(borrowed));

        pool.release(borrowed);
        assertTrue("its borrower closes it on release", pool.wasClosed(borrowed));
    }

    @Test
    public void idleSendersAreDestroyedImmediatelyOnFailure() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        Object idle = pool.acquire();
        pool.release(idle);                                  // idle now genuinely sitting in `unlocked`

        // A different sender entirely reports the failure, so `idle` stays untouched in `unlocked` right up to
        // failGroup()'s drain(unlocked) — acquire()'s own recycling never gets a chance to hand `idle` back out
        // and thereby empty `unlocked` before the failure is reported.
        Object failing = pool.newUnconnectedSender();
        pool.reportFailure(failing, new IllegalStateException("group loss"));

        assertTrue("nobody holds it, so it is destroyed via failGroup's drain(unlocked)", pool.wasClosed(idle));
    }
}
