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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_to_merge.pools.SenderPool;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
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
public class NjamsSender extends NjamsAbstractSender {

    /**
     * This constructor initializes a NjamsSender. It safes the njams instance,
     * the settings and gets the name for the executor threads from the settings
     * with the key: njams.sdk.communication_to_merge.
     *
     * @param njams    the njamsInstance for which the messages will be send from.
     * @param properties the Properties to initialize
     */
    public NjamsSender(Njams njams, Properties properties) {
        super(properties);
        super.setConnectablePool(new SenderPool(njams, properties));
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(MINQUEUELENGTH, MAXQUEUELENGTH, IDLETIME, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAXQUEUELENGTH), THREADFACTORY,
                new MaxQueueLengthHandler(properties));
        super.setExecutor(threadPoolExecutor);
    }
}
