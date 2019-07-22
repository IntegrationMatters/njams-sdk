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
package com.im.njams.sdk.communication_to_merge.connection.sender;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_to_merge.Communication;
import com.im.njams.sdk.communication_to_merge.connectable.sender.AbstractSender;
import com.im.njams.sdk.communication_to_merge.connectable.sender.TestSender;
import com.im.njams.sdk.communication_to_merge.connector.AbstractConnector;
import com.im.njams.sdk.communication_to_merge.connector.Connector;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the NjamsSender
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
//public class NjamsSenderTest extends AbstractTest {
public class NjamsSenderTest {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSenderTest.class);

    private static Settings SETTINGS;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeClass
    public static void createSettings() {
        SETTINGS = new Settings();
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        SETTINGS.setProperties(props);
    }

    //    public NjamsSenderTest() {
//        super(SETTINGS);
//    }

    /**
     * Test of close method, of class NjamsSender.
     */

    @Test
    public void testStop() {
        NjamsSender sender = new NjamsSender(null, Transformer.decode(SETTINGS.getProperties()));
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
        sender.stop();
    }


    /**
     * Test of initialize, of class NjamsSender.
     */

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(null, Transformer.decode(settings.getProperties()));
    }


    /**
     * Test of initialize, of class NjamsSender.
     */

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentSet2SenderThreadIdleTime() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(null, settings.getProperties());
    }

    /**
     * Test of initialize, of class NjamsSender.
     */

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxQueueLengthLessThanMinQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "5");
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "4");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(null, Transformer.decode(settings.getProperties()));
    }

    /**
     * Test of initialize, of class NjamsSender.
     */

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMinQueueLength() {
        Settings settings = new Settings();
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "-1");
        settings.setProperties(props);
        NjamsSender njamsSender = new NjamsSender(null, Transformer.decode(settings.getProperties()));
    }

    /**
     * Note that the amount of NjamsSenderThreads >= the amount of TestSender objects
     */
    @Test
    public void testConfiguredNjamsSender() {
        final int MAX_LOGMESSAGES_SENT = 100;
        Properties props = new Properties();
        props.put(Communication.COMMUNICATION, TestSender.NAME);
        props.put(Settings.PROPERTY_MIN_QUEUE_LENGTH, "3");
        props.put(Settings.PROPERTY_MAX_QUEUE_LENGTH, "10");
        props.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");
        NjamsSender sender = new NjamsSender(null, props);
        ThreadPoolExecutor executor = sender.getExecutor();
        assertEquals(0, executor.getActiveCount());
        assertEquals(3, executor.getCorePoolSize());
        assertEquals(10, executor.getMaximumPoolSize());
        assertEquals(5000L, executor.getKeepAliveTime(TimeUnit.MILLISECONDS));
        for (int i = 0; i < MAX_LOGMESSAGES_SENT; i++) {
            sender.send(new LogMessage());
        }
        //This is for joining the threads
        sender.stop();
        assertEquals(MAX_LOGMESSAGES_SENT, executor.getCompletedTaskCount());
        LOG.info("{} NjamsSender-Threads have been used.", executor.getLargestPoolSize());
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
        NjamsSender sender = new NjamsSender(null, Transformer.decode(SETTINGS.getProperties()));

        int messagesToSend = 1000;
        for (int i = 0; i < messagesToSend; i++) {
            Thread t = new Thread(() -> sender.send(null));
            t.start();
        }
        while (counter.get() < ExceptionSenderConnector.TRIES) {
            Thread.sleep(100);
        }
        assertEquals(ExceptionSenderConnector.TRIES, counter.get());
        sender.stop();
        TestSender.setSenderMock(null);
        assertEquals(ExceptionSenderConnector.TRIES, counter.get());
        final int MAX_NJAMS_SENDER_THREADS_ACCEPTABLE = Integer.parseInt(SETTINGS.getProperties().getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        final int NJAMS_SENDER_THREADS_USED = sender.getExecutor().getLargestPoolSize();
        LOG.info("{} NjamsSender-Threads have been used.", MAX_NJAMS_SENDER_THREADS_ACCEPTABLE);
        assertTrue(NJAMS_SENDER_THREADS_USED <= MAX_NJAMS_SENDER_THREADS_ACCEPTABLE);
    }

    private class ExceptionSender extends AbstractSender {

        @Override
        protected Connector initialize(Properties properties) {
            return new ExceptionSenderConnector(properties, getName());
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
        public void send(CommonMessage msg) {
            connector.getNjamsConnection().onException(null);
        }

        @Override
        public String getName() {
            return "ExceptionTest";
        }
    }

    private class ExceptionSenderConnector extends AbstractConnector {

        public static final int TRIES = 10;

        private boolean isInitialConnect = true;

        public ExceptionSenderConnector(Properties properties, String name) {
            super(properties, name);
        }

        @Override
        public void connect() {
            synchronized (counter) {
                if (!isInitialConnect) {
                    if (counter.get() < TRIES) {
                        LOG.info("{} times tried to reconnect.", counter.incrementAndGet());
                        throw new NjamsSdkRuntimeException("" + counter.get());
                    }
                } else {
                    isInitialConnect = false;
                    LOG.info("Initial connect");
                    throw new NjamsSdkRuntimeException("Initial connect");
                }
            }
        }

        @Override
        public void close() {

        }
    }
}
