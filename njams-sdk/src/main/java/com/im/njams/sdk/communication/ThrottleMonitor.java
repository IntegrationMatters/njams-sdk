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
 * This class is used to monitor message throttling. It issues infrequent info messages to the log file if messages
 * are could not be processed immediately. Subclassable to allow tests to inject an observing instance via
 * {@link #setInstance(ThrottleMonitor)}.
 *
 * @author cwinkler
 *
 */
public class ThrottleMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleMonitor.class);

    /** The real implementation, restored whenever the override is cleared. */
    private static final ThrottleMonitor DEFAULT = new ThrottleMonitor();
    private static volatile ThrottleMonitor instance = DEFAULT;

    private long lastMessage = System.currentTimeMillis();
    private long nextMessage = 0;
    private int throttleCount = 0;
    private int lastThrottleCount = 0;

    /** Subclassable so a test can inject an observing instance; not intended for production subclassing. */
    protected ThrottleMonitor() {
    }

    /** @return the active monitor: the real implementation, or a test instance if one was injected. */
    static ThrottleMonitor getInstance() {
        return instance;
    }

    /**
     * Injects a monitor, or restores the real one when passed {@code null}. Package-private test seam.
     * The field is JVM-wide, so a test that injects MUST restore it in teardown.
     *
     * @param override the monitor to install, or {@code null} to restore the real implementation.
     */
    static void setInstance(ThrottleMonitor override) {
        instance = override != null ? override : DEFAULT;
    }

    /**
     * Adds throttle time and issues an info message if it's time. To be called for messages that could not be processed
     * immediately.
     *
     * @param ms duration in milliseconds
     */
    public static void throttle(long ms) {
        getInstance().recordThrottle(ms);
    }

    /**
     * Records throttle time, logging a throttled summary. Overridden by test instances to observe throttling
     * without depending on log configuration.
     *
     * @param ms duration in milliseconds
     */
    protected void recordThrottle(long ms) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        throttleCount += ms;
        final long now = System.currentTimeMillis();
        if (now < nextMessage) {
            return;
        }
        synchronized (this) {
            if (now >= nextMessage) {
                nextMessage = now + 60000;
                final long minutes = (now - lastMessage + 30000) / 60000;
                lastMessage = now;
                final int throttled = throttleCount - lastThrottleCount;
                lastThrottleCount = throttleCount;
                LOG.info("Throttled processing for {}ms in the last {} minutes.", throttled, minutes);
            }
        }
    }
}
