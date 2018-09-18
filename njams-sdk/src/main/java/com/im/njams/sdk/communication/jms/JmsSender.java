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
package com.im.njams.sdk.communication.jms;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.im.njams.sdk.communication.MaxQueueLengthHandler;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;

/**
 * JMS implementation for a Sender.
 *
 * @author hsiegeln
 */
public class JmsSender implements Sender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JmsSender.class);

    private JmsSenderPool senderPool = null;
    private ThreadPoolExecutor executor = null;

    /**
     * Returns the value
     * {@value com.im.njams.sdk.communication.jms.JmsConstants#COMMUNICATION_NAME}
     * as name for this Sender.
     *
     * @return the name of this JmsSender
     */
    @Override
    public String getName() {
        return JmsConstants.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Sender via the given Properties. 
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#CONNECTION_FACTORY}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#USERNAME}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#PASSWORD}
     * <li>{@value com.im.njams.sdk.communication.jms.JmsConstants#DESTINATION}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix("JmsSender-Thread").setDaemon(true).build();
        this.executor = new ThreadPoolExecutor(maxQueueLength, maxQueueLength, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));

        senderPool = new JmsSenderPool(properties);
        // try one connection
        try {
            JmsSenderImpl sender = senderPool.get();
            senderPool.close(sender);
        } catch (Exception e) {
            LOG.error("Could not initialize sender pool: ", e);
        }
    }

    @Override
    public void send(CommonMessage msg) {
        executor.execute(new Runnable() {

            @Override
            public void run() {
                JmsSenderImpl sender = senderPool.get();
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

}
