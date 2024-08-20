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
import java.util.function.Supplier;

import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Implements the <code>maxQueueLength</code> handling for senders.
 * Its behavior is controlled using the {@value NjamsSettings#PROPERTY_DISCARD_POLICY} property
 *
 * @author hsiegeln
 */
public class MaxQueueLengthHandler implements RejectedExecutionHandler {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MaxQueueLengthHandler.class);

    private final DiscardPolicy discardPolicy;
    private final Supplier<Boolean> isConnectionLost;

    @SuppressWarnings("removal")
    public MaxQueueLengthHandler(final Properties properties, final Supplier<Boolean> isConnectionLost) {
        discardPolicy = DiscardPolicy.byValue(Settings.getPropertyWithDeprecationWarning(properties,
                NjamsSettings.PROPERTY_DISCARD_POLICY, NjamsSettings.OLD_DISCARD_POLICY));
        this.isConnectionLost = isConnectionLost;
    }

    @Override
    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
        LOG.debug("Applying discard policy [{}]", discardPolicy);
        switch (discardPolicy) {
        case DISCARD:
            LOG.trace("Message discarded (discard-policy={})", discardPolicy);
            DiscardMonitor.discard();
            break;
        case ON_CONNECTION_LOSS:
            // discardPolicy onConnectionLoss must be handled also inside sender implementation,
            // as it should apply also before the buffers are exhausted!
            if (isConnectionLost.get()) {
                LOG.trace("Message discarded (discard-policy={})", discardPolicy);
                DiscardMonitor.discard();
                break;
            }

            // intentional fall-through; if !isConnectionsLost, we will block processing
        case NONE:
            // intentional fall-through; block processing;
        default:
            if (!executor.isShutdown()) {
                LOG.trace("Waiting for free slot in dispatch queue (discard-policy={})", discardPolicy);
                blockThread(r, executor);
            }
            break;
        }

    }

    private void blockThread(final Runnable r, final ThreadPoolExecutor executor) {
        try {
            // block until this entry can be added
            final long start = System.currentTimeMillis();
            executor.getQueue().put(r);
            ThrottleMonitor.throttle(System.currentTimeMillis() - start);
        } catch (final InterruptedException e) {
            // exit handler
        }
    }

}
