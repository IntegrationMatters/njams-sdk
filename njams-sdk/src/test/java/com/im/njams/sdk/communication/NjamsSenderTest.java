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

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.Settings;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the NjamsSender
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
/*
public class NjamsSenderTest extends AbstractTest {

    private static Settings SETTINGS;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeClass
    public static void createSettings() {
        SETTINGS = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        SETTINGS.setProperties(props);
    }

    public NjamsSenderTest() {
        super(SETTINGS);
    }

    */
/**
     * Test of close method, of class NjamsSender.
     *//*

    @Test
    public void testClose() {
        NjamsSender sender = new NjamsSender(njams, SETTINGS);
        ThreadPoolExecutor executor = sender.getExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(15000L);
                } catch (InterruptedException ex) {
                }
            }
        });
//        sender.close();
    }

    */
/**
     * Test of initialize, of class NjamsSender.
     *//*

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(njams, settings);
    }

    */
/**
     * Test of initialize, of class NjamsSender.
     *//*

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgument2SenderThreadIdleTime() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(njams, settings);
    }

    */
/**
     * Test of initialize, of class NjamsSender.
     *//*

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxQueueLengthLessThanMinQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "5");
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "4");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(njams, settings);
    }

    */
/**
     * Test of initialize, of class NjamsSender.
     *//*

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMinQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(njams, settings);
    }

    @Test
    public void testConfiguredNjamsSender() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "3");
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "10");
        props.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");
        settings.setProperties(props);
        NjamsSender sender = new NjamsSender(njams, settings);
        ThreadPoolExecutor executor = sender.getExecutor();
        assertEquals(0, executor.getActiveCount());
        assertEquals(3, executor.getCorePoolSize());
        assertEquals(10, executor.getMaximumPoolSize());
        assertEquals(5000L, executor.getKeepAliveTime(TimeUnit.MILLISECONDS));
        for (int i = 0; i < 100; i++) {
            sender.send(new LogMessage());
        }
        //This is for joining the threads
//        sender.close();
        assertEquals(100, executor.getCompletedTaskCount());
        executor.getLargestPoolSize();
    }


    /**
     * The TRIES in ExceptionSender +1 senders should be reconnected at the end
     * It only reconnects one sender, because the NjamsSender creates multiple TestSenders
     * that redirect the send and connect method to the static ExceptionSender.
     */
    /*@Test
    public void testReconnectingSenders() throws InterruptedException {
        //Set static ExceptionSender to redirect calls to the TestSender
        TestSender.setSenderMock(new ExceptionSender());
        NjamsSender sender = new NjamsSender(njams, SETTINGS);
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
    }*/
//}
