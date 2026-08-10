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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;

/**
 * Acceptance test: receiver reconnect logging must be logged exactly once per phase and must never flood the log
 * across many failed retries. Mirrors {@link SenderLoggingSpecTest} adapted to the receiver side.
 */
public class ReceiverLoggingSpecTest {

    private Logger receiverLogger;
    private CapturingAppender appender;

    @Before
    public void setUp() {
        receiverLogger = Logger.getLogger(AbstractReceiver.class);
        appender = new CapturingAppender();
        receiverLogger.addAppender(appender);
        receiverLogger.setLevel(Level.DEBUG);
    }

    @After
    public void tearDown() {
        receiverLogger.removeAppender(appender);
    }

    @Test
    public void reconnectLogsOnceOnLossAndOnceOnRestore() throws Exception {
        FlakyReceiver receiver = new FlakyReceiver();
        receiver.reconnect(new NjamsSdkRuntimeException("lost"));

        long lossWarnings = appender.events().stream()
            .filter(e -> e.getLevel().equals(Level.WARN))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith("Receiver connection lost"))
            .count();
        long restoreInfos = appender.events().stream()
            .filter(e -> e.getLevel().equals(Level.INFO))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith("Receiver reconnected"))
            .count();
        assertEquals("exactly one connection-lost warning", 1, lossWarnings);
        assertEquals("exactly one reconnected info", 1, restoreInfos);
    }

    private static class FlakyReceiver extends AbstractReceiver {
        private boolean failedOnce = false;

        @Override
        public String getName() {
            return "FlakyLoggingReceiver";
        }

        @Override
        public void init(com.im.njams.sdk.settings.ClientSettings settings) {
            // no-op
        }

        @Override
        public void connect() {
            if (!failedOnce) {
                failedOnce = true;
                throw new NjamsSdkRuntimeException("first attempt fails");
            }
            connectionStatus = com.im.njams.sdk.communication.ConnectionStatus.CONNECTED;
        }

        @Override
        public void stop() {
            // no-op
        }
    }

    /** Minimal in-memory log4j appender that captures every event for later assertions. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

        List<LoggingEvent> events() {
            return events;
        }

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
