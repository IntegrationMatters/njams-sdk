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
        pool.release(idle);                       // now idle in `unlocked`
        Object failing = pool.acquire();

        pool.reportFailure(failing, new IllegalStateException("group loss"));

        assertTrue("nobody holds an idle sender, so it is closed at once", pool.wasClosed(idle));
    }
}
