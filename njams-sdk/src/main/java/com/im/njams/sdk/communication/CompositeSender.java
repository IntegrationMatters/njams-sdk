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
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class enforces the maxQueueLength setting. It uses the
 * maxQueueLengthHandler to enforce the discardPolicy, if the maxQueueLength is
 * exceeded all message sending is funneled through this class, which creates
 * and uses a pool of senders to multi-thread message sending
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public class CompositeSender implements Sender {

    private static class NjamsSharedSender extends CompositeSender {
        private final Object lock = new Object();
        private int usage = 0;

        private NjamsSharedSender(Settings settings) {
            super(settings);
        }

        @Override
        public void close() {
            // do not close the singleton before shutdown
            boolean doClose;
            synchronized (lock) {
                doClose = --usage < 1;
            }
            if (doClose) {
                super.close();
                LOG.info("Closed shared sender instance.");
            }

        }

        private void take() {
            synchronized (lock) {
                usage++;
            }
        }

        private boolean isDestroyed() {
            synchronized (lock) {
                return usage < 1;
            }
        }

    }

    //The logger to log messages.
    private static final Logger LOG = LoggerFactory.getLogger(CompositeSender.class);
    private static NjamsSharedSender sharedInstance = null;
    //The senderPool where the senders will be saved.
    private SenderPool senderPool = null;

    //The executor Threadpool that send the messages to the right senders.
    private ThreadPoolExecutor executor = null;

    //The settings will be used for the name and max-queue-length
    private final Settings settings;

    //The name for the executor threads.
    private final String name;

    /**
     * This constructor initializes a NjamsSender. It saves
     * the settings and gets the name for the executor threads from the settings
     * with the key: njams.sdk.communication.
     *
     * @param settings the setting where some settings will be taken from.
     */
    public CompositeSender(Settings settings) {
        this.settings = settings;
        name = settings.getProperty(CommunicationFactory.COMMUNICATION);
        init(settings.getAllProperties());
    }

    /**
     * Returns the one shared sender instance. On first access, the instance is lazily created. All later access will
     * get the same instance until the instance has closed. Then a new instance is created if required.
     * Calling this method tracks usage of the sender instance. I.e., after <i>taking</i> a sender, it must be
     * {@link Sender#close()}d to return the instance and allow the implementation to keep track of usage. Calling
     * {@link Sender#close()} does not really close the actual sender as long as it is still being used.
     * Only when it is no longer used (close has been called as often as it has been taken), the real sender instance
     * will be closed finally.
     *
     * @param settings Only used if a new instance needs to be created.
     * @return The shared sender instance as explained above.
     */
    public static synchronized CompositeSender takeSharedSender(Settings settings) {
        if (sharedInstance == null || sharedInstance.isDestroyed()) {
            sharedInstance = new NjamsSharedSender(settings);
        }
        sharedInstance.take();
        LOG.debug("Providing shared sender instance (used {} times)", sharedInstance.usage);
        return sharedInstance;
    }

    /**
     * This method initializes a CommunicationFactory, a ThreadPoolExecutor and
     * a SenderPool.
     *
     * @param properties the properties for MIN_QUEUE_LENGTH, MAX_QUEUE_LENGTH
     *                   and IDLE_TIME for the sender threads.
     */
    @Override
    public void init(Properties properties) {
        int minSenderThreads = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MIN_SENDER_THREADS, "1"));
        int maxSenderThreads = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_SENDER_THREADS, "8"));
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        long idleTime = Long.parseLong(properties.getProperty(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "10000"));
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
        executor = new ThreadPoolExecutor(minSenderThreads, maxSenderThreads, idleTime, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));
        final CommunicationFactory communicationFactory = new CommunicationFactory(settings);
        senderPool = new SenderPool(communicationFactory);
    }

    /**
     * This method starts a thread that sends the message to a sender in the
     * senderpool.
     *
     * @param msg the message that will be send to the server.
     */
    @Override
    public void send(CommonMessage msg) {
        executor.execute(() -> {
            AbstractSender sender = null;
            try {
                sender = senderPool.get();
                if (sender != null) {
                    sender.send(msg);
                }
            } catch (Exception e) {
                LOG.error("could not send message {}, {}", msg, e);
            } finally {
                if (sender != null) {
                    senderPool.close(sender);
                }
            }
        });
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    public void close() {
        try {
            int waitTime = 10;
            TimeUnit unit = TimeUnit.SECONDS;
            executor.shutdown();
            boolean awaitTermination = executor.awaitTermination(waitTime, unit);
            if (!awaitTermination) {
                LOG.error("The termination time of the executor has been exceeded ({} {}).", waitTime, unit);
            }
        } catch (InterruptedException ex) {
            LOG.error("The shutdown of the sender's threadpool has been interrupted. {}", ex);
        } finally {
            //This will call the interrupt() function of the threads
            LOG.info("Shutdown now the sender's threadpool executor.");
            executor.shutdownNow();
            LOG.debug("Shutdown of the sender's threadpool executor finished.");
            senderPool.expireAll();
            LOG.debug("Expire all sender pools finished.");
        }
    }

    /**
     * This method returns the name that was set in the settings with the key
     * njams.sdk.communication.
     *
     * @return the value to key njams.sdk.communication in the
     * settings
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * This method return the ThreadPoolExecutor
     *
     * @return the ThreadPoolExecutor
     */
    ThreadPoolExecutor getExecutor() {
        return executor;
    }

}
