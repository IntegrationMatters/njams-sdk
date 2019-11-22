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
import com.faizsiegeln.njams.messageformat.v4.logmessage.Activity;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.TestSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * This class tests if flushing the logMessages while processing the jobs is
 * threadsafe.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
@Ignore
public class JobImplIT extends AbstractTest {

    //The logger
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JobImplIT.class);

    //This is a queue of sent messages
    private static final List<LogMessage> messages = Collections.synchronizedList(new ArrayList<>());

    private static final int STARTFLUSHING = 10;

    //Time that each job is executed
    private static final int EXECUTIONTIME = 100;

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

            testFlushingAndAddingAttributes(activityQuantity, upperBound - activityQuantity, stop);
            //This is for multiple flusherThreads and one creator thread.
            //This isn't supported either.
            //testFlushingAndAddingAttributes(1, upperBound - activityQuantity, stop);
            //This is for one flusherThread and multiple creator threads.
            //This isn't supported (yet), but it could happen because of a client with multiple
            //threads accessing one jobImpl.
            //testFlushingAndAddingAttributes(activityQuantity, 1, stop);
            //This is normal now, one flusherThread and one creator thread.
            //testFlushingAndAddingAttributes(1, 1, stop);
        }
        //If something was thrown, it is safed in here
        if (stop.wasThrown.get()) {
            fail(stop.ex.getMessage() + stop.ex.getCause().toString());
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
     * @throws InterruptedException is thrown when the threads sleep will be
     * interrupted.
     */
    private void testFlushingAndAddingAttributes(int activityQuantity, int flusherQuantity, NjamsSdkRuntimeExceptionWrapper stop) throws InterruptedException {
        final Set<String> attr = Collections.synchronizedSet(new HashSet<>());

        JobImpl job = createDefaultJob();
        job.start();
        AtomicInteger flushCounter = new AtomicInteger(0);
        AtomicInteger activityCounter = new AtomicInteger(0);
        final AtomicBoolean run = new AtomicBoolean(true);
        this.fillJobWithMoreAttributes(job, 10);
        final AtomicInteger activityThreadsJoined = new AtomicInteger(0);
        final AtomicInteger flusherThreadsJoined = new AtomicInteger(0);
        //Create activityCreator
        for (int i = 0; i < activityQuantity; i++) {
            Runnable act = ((Runnable) () -> {
                while (run.get() && !stop.wasThrown.get()) {
                    try {
                        for (int j = 0; j <= 10; j++) {
                            ActivityImpl actImpl = createFullyFilledActivity(job);
                            //This is a critical part, because it fills a map.
                            this.fillActivityWithMoreAttributes(actImpl, 10);
                            attr.addAll(actImpl.getAttributes().keySet());
                            actImpl.end();
                            activityCounter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        stop.setException(new NjamsSdkRuntimeException("Exception in activityThread was thrown: ", e));
                        run.set(false);
                    }
                }
                //exit the while loop
                activityThreadsJoined.incrementAndGet();
            });
            Thread t = new Thread(act);
            t.start();
        }
        //After STARTFLUSHING time the flusherthreads will be generated.
        Thread.sleep(STARTFLUSHING);
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
                //exit the while loop
                flusherThreadsJoined.incrementAndGet();
            });
            Thread t = new Thread(flush);
            t.start();
        }
        //Let every thread work for EXECUTIONTIME ms.
        Thread.sleep(EXECUTIONTIME);
        //Stop the threads
        run.set(false);
        while (!(activityThreadsJoined.get() == activityQuantity && flusherThreadsJoined.get() == flusherQuantity)) {
            Thread.sleep(100);
        }
        //FlusherThreads and ActivityCreatorThreads have finished
        job.end();
        flushCounter.incrementAndGet();
        //wait for all messages to be sent
        while (messages.size() != flushCounter.get()) {
            LOG.info("Waiting...");
            Thread.sleep(1000);
        }

        LOG.info("{} ActivityThreadsJoined, {} FlusherThreadsJoined", activityThreadsJoined.get(), flusherThreadsJoined.get());
        LOG.info("{} Flusherthreads flushed {} times.", flusherQuantity, flushCounter.get());
        LOG.info("{} ActivityCreatorThreads created {} activities.", activityQuantity, activityCounter.get());
        long before = System.currentTimeMillis();
        try {

            //There should be less or equal 11 attributes more in the job than in all activities combined.
            //uncomment this for checking the attributes if they have been set in the job aswell
            checkAttributes(attr, job, stop);
            //uncomment this for checking the attributes that were in the job, if they are in the logmessage aswell.
            //checkAllMessages(messages, job, stop);
            //The last message after job.end() should have all attributes that have been sent.

            if (!messages.isEmpty()) {
                LogMessage lastMessage = messages.get(0);
                for (LogMessage message : messages) {
                    if (message.getMessageNo() > lastMessage.getMessageNo()) {
                        lastMessage = message;
                    }
                }
                checkLastMessage(lastMessage, job, stop);
            }

        } finally {
            attr.clear();
            messages.clear();
            long after = System.currentTimeMillis();
            LOG.info("Checking attributes took {} ms.", after - before);
        }
    }

    private void checkLastMessage(LogMessage message, JobImpl job, NjamsSdkRuntimeExceptionWrapper stop) {
        //Check if all attributes of the activities of the last logmessage are safed in the attributes of the logmessage itself
        //logMessage.attributes > sum(logMessage.activities.attributes)
        //logMessage.attributes = sum(logMessage.activities.attributes) + (job.getAttributes - sum(logMessage.activities.attributes))

        List<Activity> activities = message.getActivities();
        if (activities != null) {
            activities.forEach(activity -> {
                checkLogmessage(activity, message, stop);
            });
        }

        //Check if all attributes of the job are safed in the logmessages attributes    
        checkAttributes(message.getAttributes().keySet(), job, stop);
    }

    private void checkLogmessage(Activity act, LogMessage message, NjamsSdkRuntimeExceptionWrapper stop) {
        //This checks if all Strings in attributes of the activity are in the attributes of the logmessage aswell.
        Set<String> attributes = act.getAttributes().keySet();
        if (attributes != null) {
            List<String> exceptions = new ArrayList<>();
            for (String key : attributes) {
                String attribute = message.getAttributes().get(key);
                if (attribute != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("LogMessage: AttributeKey: {}, AttributeValue: {}", key, attribute);
                    }
                } else {
                    LOG.error("Logmessage: Attribute with key {} hasn't been found! (Activity {})", key, act);
                    exceptions.add(key);
                }
            }
            if (!exceptions.isEmpty()) {
                stop.setException(new NjamsSdkRuntimeException("Not all Attributes of job have been added to the logmessage!", new UnsupportedOperationException(String.join(", ", exceptions) + " weren't found.")));
                LOG.error("{} attributes haven't been found!", exceptions.size());
            }
        }
    }

    private void checkAllMessages(List<LogMessage> messages, JobImpl job, NjamsSdkRuntimeExceptionWrapper stop) {
        //Check if all attributes of the activities of the logmessage are safed in the attributes of the logmessage itself
        //logMessage.attributes > sum(logMessage.activities.attributes)
        //logMessage.attributes = sum(logMessage.activities.attributes) + (job.getAttributes - sum(logMessage.activities.attributes))

        Set<String> attr = Collections.synchronizedSet(new HashSet<>());
        messages.forEach(message -> {
            List<Activity> activities = message.getActivities();
            if (activities != null) {
                activities.forEach(activity -> {
                    checkLogmessage(activity, message, stop);
                });
            }

            //This is used later
            Set<String> attributeKeys = message.getAttributes().keySet();
            if (attributeKeys != null) {
                attr.addAll(attributeKeys);
            }
        });
        //Check if all attributes of the job are safed in the logmessages attributes    
        checkAttributes(attr, job, stop);
    }

    private void checkAttributes(Set<String> attr, JobImpl job, NjamsSdkRuntimeExceptionWrapper stop) {
        //Checks if all attributes that were given are in the job's attributes aswell
        List<String> exceptions = new ArrayList<>();
        for (String key : attr) {
            String attribute = job.getAttribute(key);
            if (attribute != null) {
                LOG.trace("Attributes: AttributeKey: {}, AttributeValue: {}", key, attribute);
            } else {
                LOG.error("Attributes: Key {} hasn't been found", key);
                exceptions.add(key);
            }
        }
        if (!exceptions.isEmpty()) {
            stop.setException(new NjamsSdkRuntimeException("Not all Attributes of the activities have been added to the job!", new UnsupportedOperationException(String.join(", ", exceptions) + " weren't found.")));
        }
    }

    /**
     * This method fills the activity with more attributes.
     *
     * @param impl the activity where the attributes will be added
     * @param number the number of added attributes
     */
    private void fillActivityWithMoreAttributes(ActivityImpl impl, int number) {
        for (int i = 0; i <= number; i++) {
            int j = ThreadLocalRandom.current().nextInt();
            impl.addAttribute("a" + j, "a" + j);
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
     * This class wraps the thrown NjamsSdkRuntimeException in here and is a
     * safes if a exception has been safed in here.
     */
    private static class NjamsSdkRuntimeExceptionWrapper {

        //This safes the actual exception that was thrown
        private volatile NjamsSdkRuntimeException ex = null;

        //This safes if something was thrown, then the test will terminate
        private volatile AtomicBoolean wasThrown = new AtomicBoolean(false);

        /**
         * This method sets the exception if this method was called the first
         * time, furthermore the field wasThrown is set to true.
         *
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
    private static class SenderMock implements Sender {

        /**
         * This method does nothing
         *
         * @param properties nothing to do with these
         */
        @Override
        public void init(Properties properties) {
            //Do nothing
        }

        /**
         * This method safes all sent messages in the messages queue.
         *
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
         *
         * @return name of the TestSender.
         */
        @Override
        public String getName() {
            return TestSender.NAME;
        }

        @Override
        public void setNjams(Njams njams) {
            //Do nothing
        }
    }
}
