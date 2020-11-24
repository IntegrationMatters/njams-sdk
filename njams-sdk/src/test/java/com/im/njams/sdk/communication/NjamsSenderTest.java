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

import static org.junit.Assert.assertEquals;

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
        SETTINGS.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
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
            try {
                t.set(Thread.currentThread());
                while(true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException ex) {
            }
        });
        Thread.sleep(1000);
        sender.close();
        assertEquals(Thread.State.TERMINATED, t.get().getState());
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxSenderThreads() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MAX_SENDER_THREADS, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgument2SenderThreadIdleTime() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxSenderThreadsLessThanMinSenderThreads() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MIN_SENDER_THREADS, "5");
        settings.put(Settings.PROPERTY_MAX_SENDER_THREADS, "4");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMinSenderThreads() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MIN_SENDER_THREADS, "-1");
        NjamsSender njamsSender = new NjamsSender(settings);
    }

    @Test
    public void testConfiguredNjamsSender() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MIN_SENDER_THREADS, "3");
        settings.put(Settings.PROPERTY_MAX_SENDER_THREADS, "10");
        settings.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");

        NjamsSender sender = new NjamsSender(settings);
        ThreadPoolExecutor executor = sender.getExecutor();
        assertEquals(0, executor.getActiveCount());
        assertEquals(3, executor.getCorePoolSize());
        assertEquals(10, executor.getMaximumPoolSize());
        assertEquals(5000L, executor.getKeepAliveTime(TimeUnit.MILLISECONDS));
        for (int i = 0; i < 100; i++) {
            sender.send(new LogMessage());
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
            Thread t = new Thread(() -> sender.send(null));
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

    private class ExceptionSender extends AbstractSender {

        public static final int TRIES = 5;

        @Override
        public void connect() throws NjamsSdkRuntimeException {
            synchronized (counter) {
                if (counter.getAndIncrement() < TRIES) {
                    System.out.println(counter.get() + " times tried to reconnect.");
                    throw new NjamsSdkRuntimeException("" + counter.get());
                } else {
                    connectionStatus = ConnectionStatus.CONNECTED;
                }
            }
        }

        @Override
        public void send(CommonMessage msg) {
            onException(null);
        }

        @Override
        protected void send(LogMessage msg) throws NjamsSdkRuntimeException {

        }

        @Override
        protected void send(ProjectMessage msg) throws NjamsSdkRuntimeException {

        }

        @Override
        protected void send(TraceMessage msg) throws NjamsSdkRuntimeException {

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
