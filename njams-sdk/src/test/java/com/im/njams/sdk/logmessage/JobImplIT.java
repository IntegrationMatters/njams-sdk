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
package com.im.njams.sdk.logmessage;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.TestSender;

import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * This class tests if flushing the logMessages while processing the jobs is
 * threadsafe.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JobImplIT extends AbstractTest {

    //The logger
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JobImplIT.class);

    //This is a queue of sent messages
    final static Queue<LogMessage> messages = new ConcurrentLinkedQueue<>();

    /**
     * Initializes the AbstractTest
     */
    public JobImplIT() {
        super();
        TestSender.setSenderMock(new SenderMock());
    }

    /**
     * This test doesn't guarantee that flushing and creating activities is
     * threadsafe, but it has a high probability.
     *
     * @throws java.lang.InterruptedException is thrown when the thread sleeps
     * will be interrupted.
     */
    @Test
    public void testFlushingAndCreatingActivities() throws InterruptedException {
        //Change this for more or less threads that interact.
        int upperBound = 10;
        NjamsSdkRuntimeExceptionWrapper stop = new NjamsSdkRuntimeExceptionWrapper();
        for (int activityQuantity = 0; activityQuantity <= upperBound && !stop.wasThrown.get(); activityQuantity++) {
            //This is for more than one flushtasks and activitytasks,
            //this is not supported by the SDK, because of single threaded LogMessageFlushTask
            //And (normally) single threaded job interaction.

            //testFlushingAndAddingAttributes(activityQuantity, upperBound - activityQuantity, stop);
            //This is for multiple flusherThreads and one creator thread.
            //This isn't supported either.
            //testFlushingAndAddingAttributes(1, upperBound - activityQuantity, stop);
            //This is for one flusherThread and multiple creator threads.
            //This isn't supported (yet), but it could happen because of a client with multiple
            //threads accessing one jobImpl.
            //testFlushingAndAddingAttributes(activityQuantity, 1, stop);
            //This is normal now, one flusherThread and one creator thread.
            testFlushingAndAddingAttributes(1, 1, stop);
        }
        //Wait for all threads to finish
        Thread.sleep(1000);
        //If something was thrown, it is safed in here
        if (stop.wasThrown.get()) {
            fail(stop.ex.getMessage() + stop.ex.getCause().toString());
        }

        long before = System.currentTimeMillis();
        try {
            //uncomment this for checking the attributes
            //checkAllMessages();
        } finally {
            long after = System.currentTimeMillis();
            LOG.info("Checking attributes took {} ms.", after - before);
        }
    }

    /**
     * This method tests if the the job's flushing and the job creating new
     * activities does not interleave.
     *
     * @param activityQuantity the number of threads that produce new activities
     * @param flusherQuantity the number of threads that flush the job
     * @param stop this is synchronization object where the an exception will be
     * safed is any is thrown and, if something was thrown, that the threads
     * stop running.
     * @throws InterruptedException is thrown when the thread sleeps will be
     * interrupted.
     */
    private void testFlushingAndAddingAttributes(int activityQuantity, int flusherQuantity, NjamsSdkRuntimeExceptionWrapper stop) throws InterruptedException {
        JobImpl job = createDefaultJob();
        job.start();
        AtomicInteger flushCounter = new AtomicInteger(0);
        AtomicInteger activityCounter = new AtomicInteger(0);
        final AtomicBoolean run = new AtomicBoolean(true);

        //Create activityCreator
        for (int i = 0; i < activityQuantity; i++) {
            Runnable act = ((Runnable) () -> {
                while (run.get() && !stop.wasThrown.get()) {
                    try {
                        for (int j = 0; j <= 10; j++) {
                            ActivityImpl actImpl = createFullyFilledActivity(job);
                            //This is a critical part, because it fills a map.
                            this.fillJobWithMoreAttributes(job, 10);
                            this.fillActivityWithMoreAttributes(actImpl, 10);
                            activityCounter.incrementAndGet();
                        }

                    } catch (Exception e) {
                        stop.setException(new NjamsSdkRuntimeException("Exception in activityThread was thrown: ", e));
                        run.set(false);
                    }
                }
                try {
                    Thread.currentThread().join(500);
                } catch (InterruptedException ex) {
                    stop.setException(new NjamsSdkRuntimeException("ActivityCreatorThread was interrupted: ", ex));

                }
            });
            Thread t = new Thread(act);
            t.start();
        }
        //Create flusher
        for (int i = 0; i < flusherQuantity; i++) {
            Runnable flush = ((Runnable) () -> {
                while (run.get() && !stop.wasThrown.get()) {
                    try {
                        job.flush();
                        flushCounter.incrementAndGet();
                    } catch (Exception e) {
                        stop.setException(new NjamsSdkRuntimeException("Exception in flusherThread was thrown: ", e));
                        run.set(false);
                    }
                    try {
                        synchronized (this) {
                            this.wait(10);
                        }
                    } catch (InterruptedException ex) {
                        LOG.error("", ex);
                    }
                }
                try {
                    Thread.currentThread().join(100);
                } catch (InterruptedException ex) {
                    stop.setException(new NjamsSdkRuntimeException("FlusherThread was interrupted: ", ex));
                }
            });
            Thread t = new Thread(flush);
            t.start();
        }
        //Let every thread work for 1000 ms.
        Thread.sleep(1000);
        //Stop the threads
        run.set(false);
        LOG.info("{} Flusherthreads flushed {} times.", flusherQuantity, flushCounter.get());
        LOG.info("{} ActivityCreatorThreads created {} activities.", activityQuantity, activityCounter.get());
    }

    /**
     * This method fills the activity with more attributes.
     *
     * @param impl the activity where the attributes will be added
     * @param number the number of added attributes
     */
    private void fillActivityWithMoreAttributes(ActivityImpl impl, int number) {
        for (int i = 0; i <= number; i++) {
            impl.addAttribute("a" + i, "a" + i);
        }
    }

    /**
     * This method fills the job with more attributes.
     *
     * @param impl the job where the attributes will be added
     * @param number the number of added attributes
     */
    private void fillJobWithMoreAttributes(JobImpl impl, int number) {
        for (int i = 0; i <= number; i++) {
            impl.addAttribute("j" + i, "j" + i);
        }
    }

    /**
     * This method checks all logmessages if each attribute of each activity has
     * been safed in the logmessage.attributes field aswell. If not, fail() will
     * be called.
     */
    private void checkAllMessages() {
        //For all messages
        messages.forEach(message -> {
            if (message.getActivities() != null) {
                //For all activities of this message
                message.getActivities().
                        forEach(activity
                                -> {
                            if (activity.getAttributes() != null) {
                                //For all attributes of this activity
                                activity.getAttributes().keySet().
                                        forEach(attributeKey -> {
                                            //Are the attributes keys of the activity in the attributes of the logmessage aswell?
                                            //The values can be different because they can be overridden.
                                            if (message.getAttributes() != null && !message.getAttributes().containsKey((String) attributeKey)) {
                                                fail();
                                            }
                                        });
                            }
                        }
                        );
            }
        });
    }

    /**
     * This class wraps the thrown NjamsSdkRuntimeException in here and is a
     * safes if a exception has been safed in here.
     */
    private class NjamsSdkRuntimeExceptionWrapper {

        //This safes the actual exception that was thrown
        private volatile NjamsSdkRuntimeException ex = null;

        //This safes if something was thrown, then the test will terminate
        private volatile AtomicBoolean wasThrown = new AtomicBoolean(false);

        /**
         * This method sets the exception if this method was called the first time,
         * furthermore the field wasThrown is set to true.
         * @param ex the exception to set.
         */
        public synchronized void setException(NjamsSdkRuntimeException ex) {
            if (this.ex == null) {
                this.ex = ex;
                wasThrown.set(true);
            }
        }
    }

    /**
     * This class is for fetching the messages that would be sent out.
     */
    private class SenderMock implements Sender {

        /**
         * This method does nothing
         * @param properties nothing to do with these
         */
        @Override
        public void init(Properties properties) {
            //Do nothing
        }

        /**
         * This method safes all sent messages in the messages queue.
         * @param msg 
         */
        @Override
        public void send(CommonMessage msg) {
            if (msg instanceof LogMessage) {
                messages.add((LogMessage) msg);
            }
        }

        /**
         * This method does nothing
         */
        @Override
        public void close() {
            //Do nothing
        }

        /**
         * This method returns the name of the TestSender.
         * @return name of the TestSender.
         */
        @Override
        public String getName() {
            return TestSender.NAME;
        }
    }
}
