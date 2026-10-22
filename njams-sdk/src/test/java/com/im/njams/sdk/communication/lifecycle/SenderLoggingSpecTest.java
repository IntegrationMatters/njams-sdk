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

import com.im.njams.sdk.communication.SenderConnectorTestAccess;
import com.im.njams.sdk.communication.SenderPoolTestAccess;

/**
 * Acceptance test: reconnect logging must be logged exactly once per phase and must never flood the log across
 * many failed retries.
 *
 * <p>The brief for this test assumed Logback + {@code ListAppender} would be on the test classpath, but the
 * active SLF4J backend for {@code njams-sdk} tests is {@code slf4j-reload4j} (log4j 1.x API, see
 * {@code njams-sdk/pom.xml} and {@code src/test/resources/log4j.properties}) — Logback is not a dependency
 * anywhere in the module. This test therefore captures the logger of the sender group's connector with a small
 * custom {@link AppenderSkeleton} instead of a Logback {@code ListAppender}. The category is named as a string
 * because {@code SenderConnector} is package-private and not referable from this package.
 */
public class SenderLoggingSpecTest extends AbstractLifecycleSpecTest {

    private Logger senderLogger;
    private CapturingAppender appender;
    private Logger poolLogger;
    private CapturingAppender poolAppender;

    @Before
    public void setUp() {
        senderLogger = Logger.getLogger("com.im.njams.sdk.communication.SenderConnector");
        appender = new CapturingAppender();
        senderLogger.addAppender(appender);
        senderLogger.setLevel(Level.DEBUG);
        // The pool owns the per-outage report, the connector the startup one; they are asserted separately.
        poolLogger = Logger.getLogger("com.im.njams.sdk.communication.SenderPool");
        poolAppender = new CapturingAppender();
        poolLogger.addAppender(poolAppender);
        poolLogger.setLevel(Level.DEBUG);
    }

    @After
    public void tearDown() {
        senderLogger.removeAppender(appender);
        poolLogger.removeAppender(poolAppender);
    }

    @Test
    public void reconnectLogsOnceOnStartAndOnceOnSuccess() throws Exception {
        // Driven through the pool, not the connector, because the pool is where an outage is reported: a test
        // that pokes SenderConnector.startReconnect directly skips the only code path that ever logs it.
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.awaitStartup(5000);

        // lose the connection: fail a few reconnect attempts, then succeed
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("lost"));
        // allow a failing attempt to happen, then let the next attempt succeed
        LifecycleTestTransport.connectAttemptedLatch().await(2000, TimeUnit.MILLISECONDS);
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);

        awaitReconnectSuccessLogged();

        assertEquals("exactly one outage report", 1, countAtLevel(poolAppender, Level.WARN));
        assertEquals("exactly one reconnect-success info", 1,
            countMessagesStartingWith(Level.INFO, "Reconnected sender"));
    }

    @Test
    public void manyFailedReconnectAttemptsDoNotFloodTheLog() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.awaitStartup(5000);

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("lost"));

        // drive several failed attempts, each observed via the attempt latch -- no fixed sleeps
        final int failedAttemptsToObserve = 5;
        for (int i = 0; i < failedAttemptsToObserve; i++) {
            LifecycleTestTransport.connectAttemptedLatch().await(5000, TimeUnit.MILLISECONDS);
        }

        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.SUCCEED);
        awaitReconnectSuccessLogged();
        assertTrue("sender must eventually reconnect once failures stop",
            pool.awaitRecovered(15, TimeUnit.SECONDS));

        assertEquals("the outage is reported once regardless of retry count", 1,
            countAtLevel(poolAppender, Level.WARN));
        assertEquals("routine retries must never log at warn level", 0, countAtLevel(Level.WARN));
        assertEquals("routine retries must never log at error level", 0, countAtLevel(Level.ERROR));
    }

    @Test
    public void failedStartupUnderFailFastLogsOnceAtError() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.beginConnect();
        s.awaitStartup(5000);

        assertEquals("startup failure reported exactly once at error", 1, countAtLevel(Level.ERROR));
        assertEquals("and never additionally at warn", 0, countAtLevel(Level.WARN));
        assertTrue("the cause belongs in the message text",
            messageAtLevelContains(Level.ERROR, "LIFECYCLE_TEST"));
    }

    @Test
    public void failedStartupUnderReconnectPolicyLogsOnceAtWarn() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.createWithReconnectBeforeConnected();
        s.beginConnect();
        s.awaitStartup(5000);

        assertEquals("a recoverable startup failure is not an error", 0, countAtLevel(Level.ERROR));
        assertEquals("reported once at warn", 1, countAtLevel(Level.WARN));
    }

    @Test
    public void startupFailureDuringShutdownIsNotReportedAsTerminal() throws Exception {
        LifecycleTestTransport.setSenderMode(LifecycleTestTransport.ConnectMode.FAIL);
        SenderConnectorTestAccess s = SenderConnectorTestAccess.create();
        s.setShouldShutdown(true);
        s.beginConnect();
        s.awaitStartup(2000);

        assertEquals("a shutdown-induced failure is not a startup error", 0, countAtLevel(Level.ERROR));
    }

    @Test
    public void anOutageIsReportedOnceAtWarnWithItsCause() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));

        assertEquals("exactly one outage report", 1, countAtLevel(poolAppender, Level.WARN));
        assertTrue("the cause belongs in the message text",
            messageAtLevelContains(poolAppender, Level.WARN, "broker gone"));
    }

    @Test
    public void furtherFailuresWithinOneOutageDoNotReportAgain() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker still gone"));

        assertEquals("one outage, one report", 1, countAtLevel(poolAppender, Level.WARN));
    }

    @Test
    public void anOutageOnASharedGroupIsReportedOncePerGroup() throws Exception {
        SenderPoolTestAccess pool = SenderPoolTestAccess.create("none");
        pool.addExceptionListener((exception, msg) -> { });
        pool.addExceptionListener((exception, msg) -> { });
        pool.reportFailure(pool.newUnconnectedSender(), new IllegalStateException("broker gone"));

        assertEquals("per group, not per listener", 1, countAtLevel(poolAppender, Level.WARN));
    }

    /**
     * Polls until the "Reconnected sender" success log has actually been captured. Polling on the group's
     * connected state instead would race the reconnect loop: the coordinator is marked connected a few
     * instructions before the success line is written, so a test thread could observe "connected" and assert
     * before that log line exists. Waiting for the log event itself removes that race. Progress is still driven
     * only by the attempt latch, never a fixed sleep.
     */
    private void awaitReconnectSuccessLogged() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (countMessagesStartingWith(Level.INFO, "Reconnected sender") == 0 && System.nanoTime() < deadline) {
            LifecycleTestTransport.connectAttemptedLatch().await(200, TimeUnit.MILLISECONDS);
        }
    }

    private long countMessagesStartingWith(Level level, String prefix) {
        return countMessagesStartingWith(appender, level, prefix);
    }

    private long countAtLevel(Level level) {
        return countAtLevel(appender, level);
    }

    private boolean messageAtLevelContains(Level level, String text) {
        return messageAtLevelContains(appender, level, text);
    }

    private static long countMessagesStartingWith(CapturingAppender from, Level level, String prefix) {
        return from.events().stream()
            .filter(e -> e.getLevel().equals(level))
            .filter(e -> String.valueOf(e.getRenderedMessage()).startsWith(prefix))
            .count();
    }

    private static long countAtLevel(CapturingAppender from, Level level) {
        return from.events().stream().filter(e -> e.getLevel().equals(level)).count();
    }

    /**
     * Asserts on the rendered message only, never on the attached throwable: the point of the contract under
     * test is that the reason is readable without digging into a stack trace.
     */
    private static boolean messageAtLevelContains(CapturingAppender from, Level level, String text) {
        return from.events().stream()
            .filter(e -> e.getLevel().equals(level))
            .anyMatch(e -> String.valueOf(e.getRenderedMessage()).contains(text));
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
