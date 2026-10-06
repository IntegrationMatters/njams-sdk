package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import com.im.njams.sdk.settings.ClientSettings;

/**
 * Tests {@link NjamsMetadata#printStartupBanner(ClientSettings)}.
 */
public class NjamsMetadataTest {

    private NjamsMetadata newMetadata() {
        return new NjamsMetadata(Path.of("SDK4", "TEST"), "1.0", "sdk4", new LifecycleState());
    }

    private ClientSettings settingsOf(Map<String, String> entries) {
        return ClientSettings.from(new HashMap<>(entries));
    }

    @Test
    public void printStartupBannerLogsAllSettingsEntries() {
        Map<String, String> raw = new HashMap<>();
        raw.put("njams.sdk.communication", "HTTP");
        raw.put("njams.sdk.http.base.url", "http://localhost:8080");
        ClientSettings settings = settingsOf(raw);

        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(NjamsMetadata.class);
        logger.addAppender(appender);
        try {
            newMetadata().printStartupBanner(settings);

            assertEquals(1, appender.infosContaining("njams.sdk.communication = HTTP").size());
            assertEquals(1, appender.infosContaining("njams.sdk.http.base.url = http://localhost:8080").size());
        } finally {
            logger.removeAppender(appender);
        }
    }

    @Test
    public void printStartupBannerLogsDebugSettingsWarningWhenDebugKeyPresent() {
        Map<String, String> raw = new HashMap<>();
        raw.put("njams.sdk.debug.messagedir", "/tmp/messages");
        ClientSettings settings = settingsOf(raw);

        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(NjamsMetadata.class);
        logger.addAppender(appender);
        try {
            newMetadata().printStartupBanner(settings);

            assertEquals(1, appender.warningsContaining("Debug settings").size());
        } finally {
            logger.removeAppender(appender);
        }
    }

    @Test
    public void printStartupBannerMatchesDebugKeyUnderAnyMiddleSegment() {
        Map<String, String> raw = new HashMap<>();
        raw.put("njams.http.debug.foo", "bar");
        ClientSettings settings = settingsOf(raw);

        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(NjamsMetadata.class);
        logger.addAppender(appender);
        try {
            newMetadata().printStartupBanner(settings);

            assertEquals(1, appender.warningsContaining("Debug settings").size());
        } finally {
            logger.removeAppender(appender);
        }
    }

    @Test
    public void printStartupBannerDoesNotLogWarningWhenNoDebugKeyPresent() {
        Map<String, String> raw = new HashMap<>();
        raw.put("njams.sdk.communication", "HTTP");
        ClientSettings settings = settingsOf(raw);

        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(NjamsMetadata.class);
        logger.addAppender(appender);
        try {
            newMetadata().printStartupBanner(settings);

            assertTrue(appender.warningsContaining("Debug settings").isEmpty());
        } finally {
            logger.removeAppender(appender);
        }
    }

    @Test
    public void printStartupBannerDoesNotLogWarningForKeyOnlyContainingDebugSubstring() {
        // "debugging" is not the literal ".debug." segment the pattern requires
        Map<String, String> raw = new HashMap<>();
        raw.put("njams.sdk.debugging.enabled", "true");
        ClientSettings settings = settingsOf(raw);

        CapturingAppender appender = new CapturingAppender();
        Logger logger = Logger.getLogger(NjamsMetadata.class);
        logger.addAppender(appender);
        try {
            newMetadata().printStartupBanner(settings);

            assertTrue(appender.warningsContaining("Debug settings").isEmpty());
        } finally {
            logger.removeAppender(appender);
        }
    }

    /** Captures log4j events for assertions. */
    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected synchronized void append(LoggingEvent event) {
            events.add(event);
        }

        synchronized List<String> infosContaining(String needle) {
            return matching(Level.INFO, needle);
        }

        synchronized List<String> warningsContaining(String needle) {
            return matching(Level.WARN, needle);
        }

        private List<String> matching(Level level, String needle) {
            List<String> result = new ArrayList<>();
            for (LoggingEvent event : events) {
                if (event.getLevel() == level && String.valueOf(event.getRenderedMessage()).contains(needle)) {
                    result.add(event.getRenderedMessage());
                }
            }
            return result;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
