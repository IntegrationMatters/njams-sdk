package com.im.njams.sdk.communication.jms.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.CapturingLogAppender;

/**
 * Verifies what is reported when a configured JMS factory is not found and the default is used instead.
 */
public class JmsFactoryUnavailableLoggingTest {

    private ClientSettings settings(String key, String value) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(key, value);
        return settings;
    }

    @Test
    public void notFoundWithUnavailableImplementationsIsLoggedAsWarning() {
        CapturingLogAppender appender = CapturingLogAppender.attach(JmsFactory.class);
        try {
            JmsFactory found = JmsFactory.find(settings(NjamsSettings.PROPERTY_JMS_JMSFACTORY, "doesNotExist"), false);

            assertTrue(found instanceof JndiJmsFactory);
            assertTrue(appender.events(Level.ERROR).isEmpty());
            List<LoggingEvent> warnings = appender.events(Level.WARN);
            assertEquals(1, warnings.size());
            String message = warnings.get(0).getRenderedMessage();
            assertTrue(message, message.contains("'doesNotExist'"));
            assertTrue(message, message.contains(NjamsSettings.PROPERTY_JMS_JMSFACTORY));
            assertTrue(message, message.contains("Available: ["));
            assertTrue(message, message.contains("FailingJmsFactory could not be instantiated"));
        } finally {
            appender.detach();
        }
    }

    @Test
    public void notFoundWithoutConfiguredKeyIsNotReported() {
        CapturingLogAppender appender = CapturingLogAppender.attach(JmsFactory.class);
        try {
            JmsFactory found = JmsFactory.find(ClientSettings.from(new Properties()), false);

            assertTrue(found instanceof JndiJmsFactory);
            assertTrue(appender.events(Level.WARN).isEmpty());
            assertTrue(appender.events(Level.ERROR).isEmpty());
        } finally {
            appender.detach();
        }
    }
}
