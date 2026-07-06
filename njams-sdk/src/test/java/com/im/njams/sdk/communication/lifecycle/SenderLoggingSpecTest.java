package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Acceptance test: reconnect logging must be logged exactly once per phase and must never flood the log across
 * many failed retries.
 *
 * <p>The brief for this test assumed Logback + {@code ListAppender} would be on the test classpath, but the
 * active SLF4J backend for {@code njams-sdk} tests is {@code slf4j-reload4j} (log4j 1.x API, see
 * {@code njams-sdk/pom.xml} and {@code src/test/resources/log4j.properties}) — Logback is not a dependency
 * anywhere in the module. This test therefore captures {@link AbstractSender}'s logger with a small custom
 * {@link AppenderSkeleton} instead of a Logback {@code ListAppender}.
 */
public class SenderLoggingSpecTest {

    private Logger senderLogger;
    private CapturingAppender appender;

    @Before
    public void setUp() {
        LifecycleTestTransport.reset();
        senderLogger = Logger.getLogger(AbstractSender.class);
        appender = new CapturingAppender();
        senderLogger.addAppender(appender);
        senderLogger.setLevel(Level.DEBUG);
    }

    @After
    public void tearDown() {
        senderLogger.removeAppender(appender);
    }

    @Test
    public void reconnectLogsOnceOnStartAndOnceOnSuccess() throws Exception {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        s.beginConnect();
        s.awaitStartup(5000);

        // lose the connection: fail a few reconnect attempts, then succeed
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ((LifecycleTestSender) s).forceDisconnect();
        s.reconnect(new IllegalStateException("lost"));
        // allow a failing attempt to happen, then let the next attempt succeed
        LifecycleTestTransport.connectAttemptedLatch().await(2000, TimeUnit.MILLISECONDS);
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);

        awaitReconnectSuccessLogged();

        assertEquals("exactly one reconnect-start info", 1,
            countMessagesStartingWith(Level.INFO, "Initialized reconnect"));
        assertEquals("exactly one reconnect-success info", 1,
            countMessagesStartingWith(Level.INFO, "Reconnected sender"));
    }

    @Test
    public void manyFailedReconnectAttemptsDoNotFloodTheLog() throws Exception {
        AbstractSender s = new LifecycleTestSender();
        s.init(ClientSettings.from(LifecycleTestTransport.settings().getAllProperties()));
        s.beginConnect();
        s.awaitStartup(5000);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        ((LifecycleTestSender) s).forceDisconnect();
        s.reconnect(new IllegalStateException("lost"));

        // drive several failed attempts, each observed via the attempt latch -- no fixed sleeps
        final int failedAttemptsToObserve = 5;
        for (int i = 0; i < failedAttemptsToObserve; i++) {
            LifecycleTestTransport.connectAttemptedLatch().await(5000, TimeUnit.MILLISECONDS);
        }

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        awaitReconnectSuccessLogged();
        assertTrue("sender must eventually reconnect once failures stop", s.isConnected());

        assertEquals("reconnect-start info must stay bounded regardless of retry count", 1,
            countMessagesStartingWith(Level.INFO, "Initialized reconnect"));
        assertEquals("routine retries must never log at warn level", 0, countAtLevel(Level.WARN));
        assertEquals("routine retries must never log at error level", 0, countAtLevel(Level.ERROR));
    }

    /**
     * Polls until the "Reconnected sender" success log has actually been captured. Polling on
     * {@code isConnected()} instead would race the reconnect thread: {@code connectionStatus} flips to
     * {@code CONNECTED} inside {@code connect()}, a few instructions before {@code doReconnect()} logs the
     * success -- a test thread could observe "connected" and assert before that log line is written. Waiting
     * for the log event itself removes that race. Progress is still driven only by the attempt latch, never a
     * fixed sleep.
     */
    private void awaitReconnectSuccessLogged() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (countMessagesStartingWith(Level.INFO, "Reconnected sender") == 0 && System.nanoTime() < deadline) {
            LifecycleTestTransport.connectAttemptedLatch().await(200, TimeUnit.MILLISECONDS);
        }
    }

    private long countMessagesStartingWith(Level level, String prefix) {
        return appender.events().stream()
            .filter(e -> e.getLevel().equals(level))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith(prefix))
            .count();
    }

    private long countAtLevel(Level level) {
        return appender.events().stream().filter(e -> e.getLevel().equals(level)).count();
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
