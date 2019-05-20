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
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.connectable.Sender;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;
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
public class NjamsSender implements Sender {

    //The logger to log messages.
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSender.class);

    //The senderPool where the senders will be safed.
    private SenderPool senderPool = null;

    //The executor Threadpool that send the messages to the right senders.
    private ThreadPoolExecutor executor = null;

    //The njamsInstance to work for
    private final Njams njams;

    //The settings will be used for the name and max-queue-length
    private final Settings settings;

    /**
     * This constructor initializes a NjamsSender. It safes the njams instance,
     * the settings and gets the name for the executor threads from the settings
     * with the key: njams.sdk.communication.
     *
     * @param njams    the njamsInstance for which the messages will be send from.
     * @param settings the setting where some settings will be taken from.
     */
    public NjamsSender(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
        this.init(Transformer.decode(settings.getProperties()));
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
        CommunicationFactory communicationFactory = new CommunicationFactory(njams, settings);
        int minQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MIN_QUEUE_LENGTH, "1"));
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        long idleTime = Long.parseLong(properties.getProperty(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "10000"));
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(this.getClass().getSimpleName() + "-Thread").setDaemon(true).build();
        this.executor = new ThreadPoolExecutor(minQueueLength, maxQueueLength, idleTime, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));
        this.senderPool = new SenderPool(communicationFactory, properties);
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    public void stop() {
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
        }
    }

    @Override
    public Connector getConnector() {
        return null;
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
            Sender sender = null;
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
     * This method returns the name that was set in the settings with the key
     * njams.sdk.communication.
     *
     * @return the value to key njams.sdk.communication in the
     * settings
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
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
