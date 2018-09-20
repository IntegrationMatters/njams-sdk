/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;

/**
 * This class enforces the maxQueueLength setting. 
 * It uses the maxQueueLengthHandler to enforce the discardPolicy, if the maxQueueLength is exceeded
 * All message sending is funneled through this class, which creates and uses a pool of senders to multi-thread message sending
 * 
 * @author hsiegeln
 *
 */
public class NjamsSender implements Sender, SenderFactory {

    private SenderPool senderPool = null;
    private ThreadPoolExecutor executor = null;
    private Njams njams;
    private Settings settings;
    private String name;

    public NjamsSender(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
        this.name = settings.getProperties().getProperty(CommunicationFactory.COMMUNICATION);
        init(settings.getProperties());
    }

    @Override
    public void init(Properties properties) {
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
        this.executor = new ThreadPoolExecutor(maxQueueLength, maxQueueLength, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));
        senderPool = new SenderPool(this, properties);
    }

    @Override
    public void send(CommonMessage msg) {
        executor.execute(new Runnable() {

            @Override
            public void run() {
                Sender sender = senderPool.get();
                if (sender != null) {
                    sender.send(msg);
                }
                senderPool.close(sender);
            }
        });
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * this is called by the SenderPool, if a new sender is required
     */
    @Override
    public Sender getSenderImpl() {
        return new CommunicationFactory(njams, settings).getSender();
    }

}
