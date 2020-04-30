/*
 * Copyright (c) 2020 Faiz & Siegeln Software GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to monitor message discarding. It issues infrequent warning messages to the log file if messages
 * are discarded.
 *
 * @author cwinkler
 *
 */
public class DiscardMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(DiscardMonitor.class);

    private static long lastMessage = System.currentTimeMillis();
    private static long nextMessage = 0;
    private static int discardCount = 0;
    private static int lastDiscardCount = 0;

    private DiscardMonitor() {
        // static only
    }

    /**
     * Increments discard counter and issues a warning if it's time. To be called for message that is discarded.
     */
    public static void discard() {
        if (!LOG.isWarnEnabled()) {
            return;
        }
        discardCount++;
        final long now = System.currentTimeMillis();
        if (now < nextMessage) {
            return;
        }
        synchronized (DiscardMonitor.class) {
            if (now >= nextMessage) {
                nextMessage = now + 60000;
                final long minutes = (now - lastMessage + 30000) / 60000;
                lastMessage = now;
                final int discarded = discardCount - lastDiscardCount;
                lastDiscardCount = discardCount;
                LOG.warn("Discarded {} messages in the last {} minutes (total={}).", discarded, minutes, discardCount);
            }
        }
    }
}
