package com.im.njams.sdk.communication.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.StartupResult;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Acceptance test for SDK-494: a client with no communication type configured must learn why its startup failed
 * from a single, actionable error — not from a warning about a thread that could not be started, whose reason is
 * only recoverable by reading a stack trace.
 *
 * <p>The appender is attached to the {@code com.im.njams.sdk.Njams} category specifically, never a parent. A
 * <em>typo'd</em> communication type legitimately produces a second error from {@code CommunicationFactory}
 * listing the registered providers (SDK-491), and a broader category would capture it and make the
 * count-of-one assertion fail for a reason that is not a defect. The missing-setting case covered here has no
 * such second line.
 */
public class StartupConfigurationErrorSpecTest extends AbstractLifecycleSpecTest {

    private Logger njamsLogger;
    private CapturingAppender appender;

    @Before
    public void captureNjamsLog() {
        njamsLogger = Logger.getLogger("com.im.njams.sdk.Njams");
        appender = new CapturingAppender();
        njamsLogger.addAppender(appender);
        njamsLogger.setLevel(Level.DEBUG);
    }

    @After
    public void releaseNjamsLog() {
        njamsLogger.removeAppender(appender);
    }

    @Test
    public void startupWithoutACommunicationTypeFailsWithAnActionableError() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");

        Njams njams = new Njams(Path.of("test"), "1.0", "test", settings);

        assertEquals(StartupResult.FAIL, njams.startup());
        assertEquals("one actionable error", 1, countAtLevel(Level.ERROR));
        assertTrue("names the setting at fault",
            messageAtLevelContains(Level.ERROR, NjamsSettings.PROPERTY_COMMUNICATION));
    }

    private long countAtLevel(Level level) {
        return appender.events().stream().filter(e -> e.getLevel().equals(level)).count();
    }

    private boolean messageAtLevelContains(Level level, String text) {
        return appender.events().stream()
            .filter(e -> e.getLevel().equals(level))
            .anyMatch(e -> renderWithThrowable(e).contains(text));
    }

    /** The reason may be in the message or in the exception the message carries; both count as "stated". */
    private static String renderWithThrowable(LoggingEvent event) {
        final StringBuilder rendered = new StringBuilder(String.valueOf(event.getRenderedMessage()));
        if (event.getThrowableInformation() != null) {
            for (String line : event.getThrowableInformation().getThrowableStrRep()) {
                rendered.append(System.lineSeparator()).append(line);
            }
        }
        return rendered.toString();
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
