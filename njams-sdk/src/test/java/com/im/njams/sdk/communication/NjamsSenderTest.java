/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.Settings;

/**
 * Tests the NjamsSender
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
public class NjamsSenderTest extends AbstractTest {

    private static Settings SETTINGS;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeClass
    public static void createSettings() {
        SETTINGS = new Settings();
        SETTINGS.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
    }

    public NjamsSenderTest() {
        super(SETTINGS);
    }

    /**
     * Test of close method, of class NjamsSender.
     */
    @Test
    public void testClose() throws InterruptedException {
        NjamsSender sender = new NjamsSender(SETTINGS);
        ThreadPoolExecutor executor = sender.getExecutor();
        AtomicReference<Thread> t = new AtomicReference<>();
        executor.execute(() -> {
            t.set(Thread.currentThread());
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        //Wait for a second so the executor actually called execute before sender.close is called.
        Thread.sleep(1000);
        sender.close();
        // The thread has been interrupted, or it has already terminated after interruption.
        // However, it will not proceed with its normal processing.
        // But there still is a small window when the exception is being processed where the thread is neither interrupted
        // nor terminated.
        Thread.sleep(500);
        // Finally, after some time, the thread has definitely terminated
        assertEquals(Thread.State.TERMINATED, t.get().getState());
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxSenderThreads() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgument2SenderThreadIdleTime() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_SENDER_THREAD_IDLE_TIME, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxSenderThreadsLessThanMinSenderThreads() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "5");
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMinSenderThreads() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    @Test
    public void testConfiguredNjamsSender() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "3");
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "10");
        settings.put(NjamsSettings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");

        NjamsSender sender = new NjamsSender(settings);
        ThreadPoolExecutor executor = sender.getExecutor();
        assertEquals(0, executor.getActiveCount());
        assertEquals(3, executor.getCorePoolSize());
        assertEquals(10, executor.getMaximumPoolSize());
        assertEquals(5000L, executor.getKeepAliveTime(TimeUnit.MILLISECONDS));
        for (int i = 0; i < 100; i++) {
            sender.send(new LogMessage(), null);
        }
        //This is for joining the threads
        sender.close();
        assertEquals(100, executor.getCompletedTaskCount());
        executor.getLargestPoolSize();
    }

    /**
     * The TRIES in ExceptionSender +1 senders should be reconnected at the end
     * It only reconnects one sender, because the NjamsSender creates multiple TestSenders
     * that redirect the send and connect method to the static ExceptionSender.
     */
    @Test
    public void testReconnectingSenders() throws InterruptedException {
        //Set static ExceptionSender to redirect calls to the TestSender
        TestSender.setSenderMock(new ExceptionSender());
        NjamsSender sender = new NjamsSender(SETTINGS);
        int messagesToSend = 1000;
        for (int i = 0; i < messagesToSend; i++) {
            Thread t = new Thread(() -> sender.send(null, null));
            t.start();
        }
        while (counter.get() < ExceptionSender.TRIES) {
            Thread.sleep(100);
        }
        Thread.sleep(1000);
        //+1 for the succeeded connection
        assertEquals(counter.get(), ExceptionSender.TRIES + 1);
        TestSender.setSenderMock(null);
        assertEquals(counter.get(), ExceptionSender.TRIES + 1);
    }

    private static Settings discardPolicySettings;

    @BeforeClass
    public static void createDiscardPolicySettings() {
        discardPolicySettings = new Settings();
        discardPolicySettings.put(NjamsSettings.PROPERTY_COMMUNICATION, TestSender.NAME);
        discardPolicySettings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "3");
        discardPolicySettings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "10");
        discardPolicySettings.put(NjamsSettings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");
    }

    @Test
    public void discardPolicyNoneOnStartupTest() {
        testIfThreadBlocksOnStartup("none");
    }

    @Test
    public void discardPolicyNoneTest() {
        testIfThreadBlocks("none");
    }

    @Test
    public void onConnectionLossOnStartupTest() {
        testIfThreadBlocksOnStartup("onconnectionloss");
    }

    @Test
    public void onConnectionLossTestTest() {
        testIfThreadBlocks("onconnectionloss");
    }

    private void testIfThreadBlocks(String discardPolicy) {
        createDiscardPolicySettings();
        discardPolicySettings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        NjamsSender sender = spy(new NjamsSender(discardPolicySettings));
        ThreadPoolExecutor executor = sender.getExecutor();
        MaxQueueLengthHandler maxQueueLengthHandler =
            (MaxQueueLengthHandler) spy(executor.getRejectedExecutionHandler());
        Runnable runnable = mock(Runnable.class);
        doNothing().when(maxQueueLengthHandler).blockThread(any(), any());

        int messagesToSend = 100;
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(runnable, executor);
            }
        }
        verify(maxQueueLengthHandler, times(0)).rejectedExecution(runnable, executor);

        //Connection loss
        doThrow(new NjamsSdkRuntimeException(null)).when(sender).send(any(), any());
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(runnable, executor);
            }
        }
        verify(maxQueueLengthHandler, times(messagesToSend)).rejectedExecution(runnable, executor);
        verify(maxQueueLengthHandler, times(messagesToSend)).blockThread(runnable, executor);
    }

    private void testIfThreadBlocksOnStartup(String discardPolicy) {
        createDiscardPolicySettings();
        discardPolicySettings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, discardPolicy);
        NjamsSender sender = spy(new NjamsSender(discardPolicySettings));
        ThreadPoolExecutor executor = sender.getExecutor();
        MaxQueueLengthHandler maxQueueLengthHandler =
            (MaxQueueLengthHandler) spy(executor.getRejectedExecutionHandler());
        Runnable r = mock(Runnable.class);
        doThrow(new NjamsSdkRuntimeException(null)).when(sender).send(any(), any());
        doNothing().when(maxQueueLengthHandler).blockThread(any(), any());

        int messagesToSend = 100;
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(r, executor);
            }
        }
        verify(maxQueueLengthHandler, times(messagesToSend)).blockThread(r, executor);
    }

    @Test
    public void discardOnStartupTest() {
        createDiscardPolicySettings();
        discardPolicySettings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "discard");
        NjamsSender sender = spy(new NjamsSender(discardPolicySettings));
        ThreadPoolExecutor executor = sender.getExecutor();
        MaxQueueLengthHandler maxQueueLengthHandler =
            (MaxQueueLengthHandler) spy(executor.getRejectedExecutionHandler());
        Runnable runnable = mock(Runnable.class);
        doThrow(new NjamsSdkRuntimeException(null)).when(sender).send(any(), any());
        doNothing().when(maxQueueLengthHandler).blockThread(any(), any());

        int messagesToSend = 100;
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(runnable, executor);
            }
        }
        verify(maxQueueLengthHandler, times(messagesToSend)).rejectedExecution(runnable, executor);
        verify(maxQueueLengthHandler, times(0)).blockThread(runnable, executor);
    }

    @Test
    public void discardTest() {
        createDiscardPolicySettings();
        discardPolicySettings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "discard");
        NjamsSender sender = spy(new NjamsSender(discardPolicySettings));
        ThreadPoolExecutor executor = sender.getExecutor();
        MaxQueueLengthHandler maxQueueLengthHandler =
            (MaxQueueLengthHandler) spy(executor.getRejectedExecutionHandler());
        Runnable runnable = mock(Runnable.class);
        doNothing().when(maxQueueLengthHandler).blockThread(any(), any());

        int messagesToSend = 100;
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(runnable, executor);
            }
        }
        verify(maxQueueLengthHandler, times(0)).rejectedExecution(runnable, executor);

        //Connection loss
        doThrow(new NjamsSdkRuntimeException(null)).when(sender).send(any(), any());
        for (int i = 0; i < messagesToSend; i++) {
            try {
                sender.send(new LogMessage(), null);
            } catch (Exception e) {
                maxQueueLengthHandler.rejectedExecution(runnable, executor);
            }
        }
        verify(maxQueueLengthHandler, times(messagesToSend)).rejectedExecution(runnable, executor);
        verify(maxQueueLengthHandler, times(0)).blockThread(runnable, executor);
    }

    private class ExceptionSender extends AbstractSender {

        public static final int TRIES = 5;

        @Override
        public void connect() throws NjamsSdkRuntimeException {
            synchronized (counter) {
                if (counter.getAndIncrement() < TRIES) {
                    System.out.println(counter.get() + " times tried to reconnect.");
                    throw new NjamsSdkRuntimeException("" + counter.get());
                }
                setConnectionStatus(ConnectionStatus.CONNECTED);
            }
        }

        @Override
        public void send(CommonMessage msg, String clientId) {
            onException(null);
        }

        @Override
        protected void send(LogMessage msg, String clientId) throws NjamsSdkRuntimeException {

        }

        @Override
        protected void send(ProjectMessage msg, String clientId) throws NjamsSdkRuntimeException {

        }

        @Override
        protected void send(TraceMessage msg, String clientId) throws NjamsSdkRuntimeException {

        }

        @Override
        public void close() {

        }

        @Override
        public String getName() {
            return null;
        }
    }
}
