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

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.factories.ThreadFactoryBuilder;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.encoding.Transformer;

/**
 * This class enforces the maxQueueLength setting. It uses the
 * maxQueueLengthHandler to enforce the discardPolicy, if the maxQueueLength is
 * exceeded All message sending is funneled through this class, which creates
 * and uses a pool of senders to multi-thread message sending
 *
 * @author hsiegeln
 * @version 4.0.4
 */
public class NjamsSender implements Sender, SenderFactory {

    //The logger to log messages.
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsSender.class);
    
    //The senderPool where the senders will be safed.
    private SenderPool senderPool = null;
    
    //The executer Threadpool that send the messages to the right senders.
    private ThreadPoolExecutor executor = null;    
    
    //The njamsInstance to work for
    private final Njams njams;
    
    //The settings will be used for the name and max-queue-length
    private final Settings settings;
    
    //The name for the executer threads.
    private final String name;

    //The communicationFactory where to get new senders from.
    private CommunicationFactory communicationFactory;

    /**
     * This constructor initializes a NjamsSender. It safes the njams instance,
     * the settings and gets the name for the executer threads from the settings
     * with the key: njams.sdk.communication.
     *
     * @param njams the njamsInstance for which the messages will be send from.
     * @param settings the setting where some settings will be taken from.
     */
    public NjamsSender(Njams njams, Settings settings) {
        this.njams = njams;
        this.settings = settings;
        this.name = Transformer.decode(settings.getProperties().getProperty(CommunicationFactory.COMMUNICATION));
        init(Transformer.decode(settings.getProperties()));
    }

    /**
     * This method initializes a CommunicationFactory, a ThreadPoolExecuter and
     * a SenderPool.
     *
     * @param properties the properties for MAX_QUEUE_LENGTH
     */
    @Override
    public void init(Properties properties) {
        this.communicationFactory = new CommunicationFactory(njams, settings);
        int maxQueueLength = Integer.parseInt(properties.getProperty(Settings.PROPERTY_MAX_QUEUE_LENGTH, "8"));
        //Maybe an expected Queue length here?
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNamePrefix(getName() + "-Sender-Thread").setDaemon(true).build();
        this.executor = new ThreadPoolExecutor(maxQueueLength, maxQueueLength, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueLength), threadFactory,
                new MaxQueueLengthHandler(properties));
        this.senderPool = new SenderPool(this, properties);
    }

    /**
     * This method starts a thread that sends the message to a sender in the
     * senderpool.
     *
     * @param msg the message that will be send to the server.
     */
    @Override
    public void send(CommonMessage msg) {
        executor.execute(new Runnable() {

            @Override
            public void run() {
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
            }
        });
    }

    /**
     * This method closes the ThreadPoolExecuter safely. It awaits the
     * termination for 10 seconds, after that, an InterruptedException will be
     * thrown and the senders will be closed.
     */
    @Override
    public void close() {
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            LOG.error("The shutdown of the sender's threadpool has been interrupted. {}", ex);
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
     * This method is called by the SenderPool, if a new sender is required.
     *
     * @return The newly created sender
     */
    @Override
    public Sender getSenderImpl() {
        return communicationFactory.getSender();
    }

}
