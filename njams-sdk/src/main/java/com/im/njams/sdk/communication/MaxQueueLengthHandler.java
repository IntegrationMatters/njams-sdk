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
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.LoggerFactory;

import com.im.njams.sdk.settings.Settings;

/**
 * implements the maxQueueLength handling
 * its behavior is controlled using the njams.client.sdk.discardpolicy property
 *
 * @author hsiegeln
 *
 */
public class MaxQueueLengthHandler implements RejectedExecutionHandler {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MaxQueueLengthHandler.class);

    private final DiscardPolicy discardPolicy;

    public MaxQueueLengthHandler(Properties properties) {
        discardPolicy = DiscardPolicy.byValue(properties.getProperty(Settings.PROPERTY_DISCARD_POLICY));
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        LOG.trace("Applying discard policy [{}]", discardPolicy);
        switch (discardPolicy) {
        case DISCARD:
            LOG.debug("Message discarded");
            break;
        case ON_CONNECTION_LOSS:
            // discardPolicy onConnectionLoss must be handled inside sender implementation,
            // as the connection status cannot be checked here!
            //
            // intentional fall-through
        case NONE:
            // intentional fall-through
        default:
            if (!executor.isShutdown()) {
                try {
                    // block until this entry can be added
                    LOG.trace("Waiting for free slot in dispatch queue");
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    // exit handler
                }
            }
            break;
        }
    }

}
