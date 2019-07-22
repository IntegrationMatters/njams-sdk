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
import com.im.njams.sdk.communication_to_merge.connectable.sender.Sender;
import com.im.njams.sdk.communication_to_merge.connection.NjamsConnectable;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Todo: Write Doc
 */
public abstract class NjamsAbstractSender extends NjamsConnectable {

    //The logger to log messages.
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsAbstractSender.class);

    //The executor Threadpool that send the messages to the right senders.
    protected ThreadPoolExecutor executor = null;

    protected final int MINQUEUELENGTH, MAXQUEUELENGTH;
    protected final long IDLETIME;
    protected final ThreadFactory THREADFACTORY;

    public NjamsAbstractSender(Properties properties) {
        MINQUEUELENGTH = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MIN_QUEUE_LENGTH, "1"));
        MAXQUEUELENGTH = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        IDLETIME = Long.parseLong(properties.getProperty(Settings.PROPERTY_SENDER_THREAD_IDLE_TIME, "10000"));
        THREADFACTORY = new ThreadFactoryBuilder()
                .setNamePrefix(this.getClass().getSimpleName() + "-Thread").setDaemon(true).build();
    }

    protected void setExecutor(ThreadPoolExecutor executor){
        this.executor = executor;
    }

    protected ThreadPoolExecutor getExecutor(){
        return this.executor;
    }

    /**
     * This method closes the ThreadPoolExecutor safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    protected void stopBeforeConnectablePoolStops() {
        if(executor != null) {
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
    }

    /**
     * This method starts a thread that sends the message to a sender in the
     * senderpool.
     *
     * @param msg the message that will be send to the server.
     */
    public void send(CommonMessage msg) {
        if(executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                Sender sender = null;
                try {
                    sender = (Sender) connectablePool.get();
                    if (sender != null) {
                        sender.send(msg);
                    }
                } catch (Exception e) {
                    LOG.error("could not send message {}, {}", msg, e);
                } finally {
                    if (sender != null) {
                        connectablePool.release(sender);
                    }
                }
            });
        }
    }
}
