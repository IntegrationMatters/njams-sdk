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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        AbstractSender get1 = op.get();
        assertEquals(mockedAC, get1);
        //To clear the locked map
        op.shutdown();
        verify(get1, times(1)).close();

        AbstractSender get2 = op.get();
        assertEquals(mockedAC2, get2);
        op.close(get2);
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
        AbstractSender s1 = op.get();
        assertSame(first, s1);
        //Returning it to the pool makes it available again
        op.close(s1);
        AbstractSender s2 = op.get();
        //The unlocked sender is reused, no new one is created
        assertSame(first, s2);
        assertEquals(1, created.get());
    }

    @Test
    public void getReturnsNullAfterShutdownDeclared() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        when(mockedCF.getSender()).thenReturn(mock(AbstractSender.class));
        SenderPool op = new SenderPool(mockedCF);
        assertNotNull(op.get());
        op.declareShutdown();
        //Once shutdown is declared, no further senders are handed out
        assertNull(op.get());
    }

    @Test
    public void triggerConnectionCheckCallsOnExceptionOnEveryPooledSender() {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        AbstractSender first = mock(AbstractSender.class);
        AbstractSender second = mock(AbstractSender.class);
        java.util.Iterator<AbstractSender> senders = java.util.List.of(first, second).iterator();
        SenderPool pool = new SenderPool(mockedCF) {
            @Override
            protected AbstractSender create() {
                return senders.next();
            }
        };
        AbstractSender s1 = pool.get();
        AbstractSender s2 = pool.get();
        pool.triggerConnectionCheck();
        verify(s1, times(1)).onException(any());
        verify(s2, times(1)).onException(any());
    }

    @Test
    public void addCrossSideTriggerAllowsMultipleTriggersToAllFireOnTheSameFailure() throws InterruptedException {
        CommunicationFactory mockedCF = mock(CommunicationFactory.class);
        // A mock's reconnect() is a no-op, so the fan-out could never be observed through one — this needs a real
        // AbstractSender whose reconnect() genuinely reaches ConnectionCoordinator.beginReconnect() (where the
        // triggers actually fire).
        MinimalRealSender realSender = new MinimalRealSender();
        when(mockedCF.getSender()).thenReturn(realSender);
        SenderPool pool = new SenderPool(mockedCF);
        AtomicInteger firstTrigger = new AtomicInteger();
        AtomicInteger secondTrigger = new AtomicInteger();
        // Two calls, as would happen when a shared sender pool (njams.sdk.communication.shared=true, HTTP) is
        // wired to two different per-Njams-instance receivers — both must still fire, not just the last one.
        pool.addCrossSideTrigger(firstTrigger::incrementAndGet);
        pool.addCrossSideTrigger(secondTrigger::incrementAndGet);

        AbstractSender sender = pool.get();
        // AbstractSender.reconnect(...) requires isConnected()==false and coordinator.shouldReconnect()==true to
        // actually call beginReconnect(); a freshly created real AbstractSender starts DISCONNECTED, and its
        // coordinator defaults reconnectBeforeConnected=false/wasEverConnected=false, so shouldReconnect() would
        // be false too. Drive it through a successful beginConnect()/awaitStartup() first (mirroring
        // SenderStartupSpecTest) so ConnectionCoordinator.markStartupConnected() flips wasEverConnected to true,
        // then force it back to DISCONNECTED (mirroring SenderReconnectGatingSpecTest's forceDisconnect() pattern)
        // so the subsequent reconnect(...) call is actually eligible to proceed to beginReconnect().
        sender.beginConnect();
        assertTrue("fixture sender must complete a successful startup connect before reconnect() is exercised",
            sender.awaitStartup(5000));
        ((MinimalRealSender) sender).forceDisconnect();

        sender.reconnect(new NjamsSdkRuntimeException("test"));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((firstTrigger.get() < 1 || secondTrigger.get() < 1) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("both registered triggers must fire for the same failure", 1, firstTrigger.get());
        assertEquals("both registered triggers must fire for the same failure", 1, secondTrigger.get());
    }

    /**
     * Minimal real (non-mocked) sender fixture: connects trivially like the default {@link AbstractSender#connect()}
     * so a startup connect always succeeds, and exposes {@link #forceDisconnect()} to reach the otherwise
     * {@code protected} {@link AbstractSender#setConnectionStatus(ConnectionStatus)} for driving a subsequent
     * {@code reconnect(...)} call.
     */
    private static class MinimalRealSender extends AbstractSender {
        @Override
        public String getName() {
            return "minimal-real-sender";
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage msg, String clientId) {
            // no-op fixture
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage msg,
                String clientId) {
            // no-op fixture
        }

        @Override
        protected void send(com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage msg, String clientId) {
            // no-op fixture
        }

        void forceDisconnect() {
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }
    }
}
