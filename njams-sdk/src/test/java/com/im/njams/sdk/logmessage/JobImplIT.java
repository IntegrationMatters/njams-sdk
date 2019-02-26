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

import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
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

    /**
     * Initializes the AbstractTest
     */
    public JobImplIT() {
        super();
    }

    /**
     * This test doesn't guarantee that flushing and creating activities is
     * threadsafe, but it has a high probability.
     * @throws java.lang.InterruptedException is thrown when the thread sleeps will be interrupted.
     */
    @Test
    public void testFlushingAndCreatingActivities() throws InterruptedException {
        //Change this for more or less threads that interact.
        int upperBound = 10;
        NjamsSdkRuntimeExceptionWrapper stop = new NjamsSdkRuntimeExceptionWrapper();
        for (int activityQuantity = 0; activityQuantity <= upperBound && !stop.wasThrown.get(); activityQuantity++) {
            testFlushingAndAddingAttributes(activityQuantity, upperBound - activityQuantity, stop);
        }
        //Wait for all threads to finish
        Thread.sleep(1000);
        //If something was thrown, it is safed in here
        if (stop.wasThrown.get()) {
            //fail(stop.ex.getMessage() + stop.ex.getCause().toString());
        }
    }

    /**
     * This method tests if the the job's flushing and the job creating new
     * activities does not interleave.
     * 
     * @param activityQuantity the number of threads that produce new activities
     * @param flusherQuantity the number of threads that flush the job
     * @param stop this is synchronization object where the an exception will
     * be safed is any is thrown and, if something was thrown, that the threads
     * stop running.
     * @throws InterruptedException is thrown when the thread sleeps will be interrupted.
     */
    private void testFlushingAndAddingAttributes(int activityQuantity, int flusherQuantity, NjamsSdkRuntimeExceptionWrapper stop) throws InterruptedException {
        JobImpl job = createDefaultJob();
        job.start();
        AtomicInteger flushCounter = new AtomicInteger(0);
        AtomicInteger activityCounter = new AtomicInteger(0);
        final AtomicBoolean run = new AtomicBoolean(true);

        //Create activityCreater
        for (int i = 0; i < activityQuantity; i++) {
            Runnable act = ((Runnable) () -> {
                while (run.get() && !stop.wasThrown.get()) {
                    try {
                        ActivityImpl actImpl = createFullyFilledActivity(job);
                        //This is a critical part, because it fills a map.
                        this.fillWithMoreAttributes(actImpl, 10);
                        activityCounter.incrementAndGet();
                    } catch (Exception e) {
                        stop.setException(new NjamsSdkRuntimeException("Exception in activityThread was thrown: ", e));
                        run.set(false);

                    }
                }
                try {
                    Thread.currentThread().join(500);
                } catch (InterruptedException ex) {
                    stop.setException(new NjamsSdkRuntimeException("ActivityCreaterThread was interrupted: ", ex));

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
        LOG.info("{} ActivityCreaterThreads created {} activities.", activityQuantity, activityCounter.get());
    }

    /**
     * This method fills the activity with more attributes.
     * 
     * @param impl the activity where the attributes will be added
     * @param number the number of added attributes
     */
    private void fillWithMoreAttributes(ActivityImpl impl, int number) {
        for (int i = 0; i <= number; i++) {
            impl.addAttribute("" + i, "" + i);
        }
    }

    /**
     * This class wraps the thrown NjamsSdkRuntimeException in here and 
     * is a safes if a exception has been safed in here.
     */
    private class NjamsSdkRuntimeExceptionWrapper {

        private NjamsSdkRuntimeException ex = null;

        private final AtomicBoolean wasThrown = new AtomicBoolean(false);

        public void NjamsSdkRuntimeExceptionWrapper() {
            //Do nothing
        }

        public synchronized void setException(NjamsSdkRuntimeException ex) {
            if (this.ex == null) {
                this.ex = ex;
                wasThrown.set(true);
            }
        }
    }
}
