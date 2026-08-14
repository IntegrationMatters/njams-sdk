/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to monitor message discarding. It issues infrequent warning messages to the log file if messages
 * are discarded. Subclassable to allow tests to inject an observing instance via {@link #setInstance(DiscardMonitor)}.
 *
 * @author cwinkler
 *
 */
public class DiscardMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(DiscardMonitor.class);

    /** The real implementation, restored whenever the override is cleared. */
    private static final DiscardMonitor DEFAULT = new DiscardMonitor();
    private static volatile DiscardMonitor instance = DEFAULT;

    private long lastMessage = System.currentTimeMillis();
    private long nextMessage = 0;
    private int discardCount = 0;
    private int lastDiscardCount = 0;

    /** Subclassable so a test can inject an observing instance; not intended for production subclassing. */
    protected DiscardMonitor() {
    }

    /** @return the active monitor: the real implementation, or a test instance if one was injected. */
    static DiscardMonitor getInstance() {
        return instance;
    }

    /**
     * Injects a monitor, or restores the real one when passed {@code null}. Package-private test seam.
     * The field is JVM-wide, so a test that injects MUST restore it in teardown.
     *
     * @param override the monitor to install, or {@code null} to restore the real implementation.
     */
    static void setInstance(DiscardMonitor override) {
        instance = override != null ? override : DEFAULT;
    }

    /**
     * Increments discard counter and issues a warning if it's time. To be called for message that is discarded.
     */
    public static void discard() {
        // A volatile read plus a call site that is monomorphic in production, so the JIT inlines it. Do not
        // "optimize" this back to a static body: the indirection is what makes discarding observable in tests.
        getInstance().recordDiscard();
    }

    /**
     * Records one discarded message, logging a throttled summary. Overridden by test instances to observe
     * discards without depending on log configuration.
     */
    protected void recordDiscard() {
        if (!LOG.isWarnEnabled()) {
            return;
        }
        discardCount++;
        final long now = System.currentTimeMillis();
        if (now < nextMessage) {
            return;
        }
        synchronized (this) {
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
