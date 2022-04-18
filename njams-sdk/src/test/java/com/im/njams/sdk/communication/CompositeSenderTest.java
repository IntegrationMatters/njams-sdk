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
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests the NjamsSender
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.6
 */
public class CompositeSenderTest extends AbstractTest {

    private static Settings SETTINGS;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeClass
    public static void createSettings() {
        SETTINGS = new Settings();
        SETTINGS.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
    }

    public CompositeSenderTest() {
        super(SETTINGS);
    }

    @Test
    public void longRunningThread_forcesExecutorToShutDown_beforeThreadHasFinishedItsTask() throws InterruptedException {
        int timeToWaitForClose = 10;
        LongRunningExecutionCompositeSenderFake longRunningExecutionCompositeSender =
            new LongRunningExecutionCompositeSenderFake(SETTINGS, timeToWaitForClose);

        final int executingTimeThatIsLongerThanTheWaitingTime = timeToWaitForClose * 2;

        longRunningExecutionCompositeSender.startLongRunningProcessFor(executingTimeThatIsLongerThanTheWaitingTime);

        longRunningExecutionCompositeSender.closeAfterLongRunningThreadStarted();

        longRunningExecutionCompositeSender.checkThatTheExecutorIsTerminatedAndTheThreadWasInterrupted();
    }

    private static class LongRunningExecutionCompositeSenderFake extends CompositeSender{

        private boolean startedToSleep;

        private final String expectedExceptionMessage;
        private final TimeUnit expectedWaitTimeUnit;
        private final int expectedWaitTime;

        private String actualExceptionMesage;
        private int actualWaitTime;
        private TimeUnit actualWaitTimeUnit;
        private boolean interruptWasCalled;

        public LongRunningExecutionCompositeSenderFake(Settings settings, int timeToWaitForClose) {
            super(settings);
            this.expectedExceptionMessage = "The termination time of the executor has been exceeded ({} {}).";
            this.expectedWaitTime = timeToWaitForClose;
            this.expectedWaitTimeUnit = TimeUnit.MILLISECONDS; //Needs to be Milliseconds, because Thread.Sleep only can use Milliseconds
            this.interruptWasCalled = false;
            setClosingTimeout(expectedWaitTime, expectedWaitTimeUnit);
        }

        public void startLongRunningProcessFor(int timeToWaitForClose) {
            getExecutor().execute(() -> {
                try {
                    startedToSleep = true;
                    Thread.sleep(timeToWaitForClose*2);
                } catch (InterruptedException e) {
                    interruptWasCalled = true;
                    Thread.currentThread().interrupt();
                }
            });
        }

        public void closeAfterLongRunningThreadStarted() throws InterruptedException {
            while(!startedToSleep){
                Thread.sleep(1);
            }
            this.close();
        }

        @Override
        protected void logExecutorTerminationExceeded(String message, int waitTime, TimeUnit waitTimeUnit){
            this.actualExceptionMesage = message;
            this.actualWaitTime = waitTime;
            this.actualWaitTimeUnit = waitTimeUnit;
            super.logExecutorTerminationExceeded(message, waitTime, waitTimeUnit);
        }

        public void checkThatTheExecutorIsTerminatedAndTheThreadWasInterrupted() {
            assertThat(actualExceptionMesage, is(equalTo(expectedExceptionMessage)));
            assertThat(actualWaitTime, is(expectedWaitTime));
            assertThat(actualWaitTimeUnit, is(expectedWaitTimeUnit));
            assertThat(interruptWasCalled, is(true));
            assertThat(getExecutor().isShutdown(), is(true));
        }

    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMaxSenderThreads() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MAX_SENDER_THREADS, "-1");
        CompositeSender njamsSender = new CompositeSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgument2SenderThreadIdleTime() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "-1");
        CompositeSender njamsSender = new CompositeSender(settings);
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
        CompositeSender njamsSender = new CompositeSender(settings);
    }

    /**
     * Test of initialize, of class NjamsSender.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentMinSenderThreads() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MIN_SENDER_THREADS, "-1");
        CompositeSender njamsSender = new CompositeSender(settings);
    }

    @Test
    public void testConfiguredNjamsSender() {
        Settings settings = new Settings();
        settings.put(CommunicationFactory.COMMUNICATION, TestSender.NAME);
        settings.put(Settings.PROPERTY_MIN_SENDER_THREADS, "3");
        settings.put(Settings.PROPERTY_MAX_SENDER_THREADS, "10");
        settings.put(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "5000");

        CompositeSender sender = new CompositeSender(settings);
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
        CompositeSender sender = new CompositeSender(SETTINGS);
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
