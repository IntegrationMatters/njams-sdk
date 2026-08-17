/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

public class SenderPoolTest {

    @Test
    public void expireAll() throws Exception {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender mockedAC = mock(AbstractSender.class);
        AbstractSender mockedAC2 = mock(AbstractSender.class);
        SenderPool op = new SenderPool(mockedCF) {
            public boolean first = true;

            @Override
            protected AbstractSender create() {
                if (first) {
                    first = false;
                    return mockedAC;
                }
                return mockedAC2;
            }

        };
        //To fill the locked map
        AbstractSender get1 = op.acquire();
        assertEquals(mockedAC, get1);
        //To clear the locked map
        op.shutdown();
        verify(get1, times(1)).close();

        AbstractSender get2 = op.acquire();
        assertEquals(mockedAC2, get2);
        op.release(get2);
        op.shutdown();
        verify(get2, times(1)).close();
        //This hasn't been closed again, because it isn't in the maps anymore
        verify(get1, times(1)).close();
    }

    @Test
    public void getReusesUnlockedSenderInsteadOfCreatingNew() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender first = mock(AbstractSender.class);
        AbstractSender second = mock(AbstractSender.class);
        AtomicInteger created = new AtomicInteger();
        SenderPool op = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                return created.getAndIncrement() == 0 ? first : second;
            }
        };
        AbstractSender s1 = op.acquire();
        assertSame(first, s1);
        //Returning it to the pool makes it available again
        op.release(s1);
        AbstractSender s2 = op.acquire();
        //The unlocked sender is reused, no new one is created
        assertSame(first, s2);
        assertEquals(1, created.get());
    }

    @Test
    public void getReturnsNullAfterShutdownDeclared() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        when(mockedCF.getSender()).thenReturn(mock(AbstractSender.class));
        SenderPool op = new SenderPool(mockedCF);
        assertNotNull(op.acquire());
        op.declareShutdown();
        //Once shutdown is declared, no further senders are handed out
        assertNull(op.acquire());
    }

    /**
     * B2 (reversed): a newly created sender's {@code connect()} must not hold the pool's lock, so a slow-but-
     * reachable endpoint cannot stall an unrelated {@code acquire()} that only needs an already-idle sender.
     * Deliberately has no test timeout beyond the JVM default: under the pre-fix code this deadlocks (the second
     * {@code acquire()} would block on the same lock the first connect holds), which is exactly the regression
     * this guards against.
     */
    @Test(timeout = 10_000)
    public void connectOfANewSenderDoesNotBlockAcquireOfAnIdleSender() throws Exception {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender first = mock(AbstractSender.class);
        AbstractSender second = mock(AbstractSender.class);
        CountDownLatch secondConnectEntered = new CountDownLatch(1);
        CountDownLatch releaseSecondConnect = new CountDownLatch(1);
        doAnswer(inv -> {
            secondConnectEntered.countDown();
            releaseSecondConnect.await();
            return null;
        }).when(second).connect();

        AtomicInteger createCalls = new AtomicInteger();
        SenderPool op = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                return createCalls.getAndIncrement() == 0 ? first : second;
            }
        };

        // first is checked out, not released: the pool has no idle sender yet.
        AbstractSender s1 = op.acquire();
        assertSame(first, s1);

        // A second, concurrent acquire() finds no idle sender either, so it creates `second` and blocks in its
        // connect() - outside the lock, per the fix.
        Thread blockedCreator = new Thread(() -> op.acquire());
        blockedCreator.start();
        assertTrue("the second create's connect() must actually be in flight",
            secondConnectEntered.await(5, TimeUnit.SECONDS));

        // Release `first` back to the pool while `second` is still blocked mid-connect.
        op.release(s1);

        // This must return immediately with the now-idle `first` - it must NOT wait for `second`'s connect.
        AbstractSender s3 = op.acquire();
        assertSame("acquire() of an idle sender must not be stalled by an unrelated in-flight connect", first, s3);

        releaseSecondConnect.countDown();
        blockedCreator.join(5000);
    }

    /**
     * The new race this fix makes possible: two creates are in flight (unconnected) while the group is still
     * healthy; one fails first and elects the reconnector, the other succeeds afterwards. The late success must
     * not be published into a group that is already reconnecting - only the elected reconnector's own success may
     * clear that state.
     */
    @Test(timeout = 10_000)
    public void aLateSuccessfulConnectIsDroppedOnceASiblingHasAlreadyFailedTheGroup() throws Exception {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        List<AbstractSender> createdSenders = new CopyOnWriteArrayList<>();
        List<CountDownLatch> releaseGates = new CopyOnWriteArrayList<>();
        CountDownLatch bothEntered = new CountDownLatch(2);

        SenderPool op = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                AbstractSender sender = mock(AbstractSender.class);
                CountDownLatch release = new CountDownLatch(1);
                int index = createdSenders.size();
                createdSenders.add(sender);
                releaseGates.add(release);
                try {
                    doAnswer(inv -> {
                        bothEntered.countDown();
                        release.await();
                        if (index == 0) {
                            throw new NjamsSdkRuntimeException("first connect fails");
                        }
                        return null;
                    }).when(sender).connect();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return sender;
            }
        };

        AtomicReference<AbstractSender> resultA = new AtomicReference<>();
        AtomicReference<AbstractSender> resultB = new AtomicReference<>();
        Thread t1 = new Thread(() -> resultA.set(op.acquire()));
        Thread t2 = new Thread(() -> resultB.set(op.acquire()));
        t1.start();
        t2.start();

        assertTrue("both concurrent creates must be blocked in connect() at once - only possible because "
            + "connect() now runs outside the lock", bothEntered.await(5, TimeUnit.SECONDS));

        // Let the first-created sender fail and flip the group to reconnecting.
        releaseGates.get(0).countDown();
        long deadline = System.currentTimeMillis() + 5000;
        while (!op.isConnectionFailure() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("the sibling failure must be recorded before the second connect is released",
            op.isConnectionFailure());

        // Now let the second (later) sender connect successfully - it must discover reconnecting == true.
        releaseGates.get(1).countDown();
        t1.join(5000);
        t2.join(5000);

        assertNull("the failing create is never published", resultA.get());
        assertNull("a redundant success must not be published into an already-reconnecting group", resultB.get());
        verify(createdSenders.get(0), times(1)).close();
        verify(createdSenders.get(1), times(1)).close();
    }

    /**
     * {@code declareShutdown()} can now run while a create's {@code connect()} is still in flight outside the
     * lock. The sender must be closed once it finishes connecting, not published and not leaked.
     */
    @Test(timeout = 10_000)
    public void aSenderConnectingWhenShutdownIsDeclaredIsClosedNotPublished() throws Exception {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender sender = mock(AbstractSender.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            entered.countDown();
            release.await();
            return null;
        }).when(sender).connect();

        SenderPool op = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                return sender;
            }
        };

        AtomicReference<AbstractSender> result = new AtomicReference<>();
        Thread t = new Thread(() -> result.set(op.acquire()));
        t.start();
        assertTrue("the create must be blocked inside connect(), outside the lock", entered.await(5, TimeUnit.SECONDS));

        op.declareShutdown();
        release.countDown();
        t.join(5000);

        assertNull("a sender that finishes connecting after shutdown must not be published", result.get());
        verify(sender, times(1)).close();
    }

}
