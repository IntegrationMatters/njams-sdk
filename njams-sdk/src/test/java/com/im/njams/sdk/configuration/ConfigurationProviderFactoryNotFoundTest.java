package com.im.njams.sdk.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.CapturingLogAppender;

/**
 * Verifies what is reported when the configured {@link ConfigurationProvider} cannot be found.
 */
public class ConfigurationProviderFactoryNotFoundTest {

    private ClientSettings settingsWithProvider(String name) {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, name);
        return settings;
    }

    @Test
    public void notFoundIsLoggedAsErrorWithAvailableAndUnavailableImplementations() {
        CapturingLogAppender appender = CapturingLogAppender.attach(ConfigurationProviderFactory.class);
        try {
            try {
                new ConfigurationProviderFactory(settingsWithProvider("doesNotExist"), null)
                    .getConfigurationProvider();
                fail("IllegalArgumentException expected");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("'doesNotExist'"));
                assertTrue(e.getMessage(), e.getMessage().contains("See the log"));
                assertFalse("the exception must not carry the lists: " + e.getMessage(),
                    e.getMessage().contains("Available"));
            }
            List<LoggingEvent> errors = appender.events(Level.ERROR);
            assertEquals(1, errors.size());
            String message = errors.get(0).getRenderedMessage();
            assertTrue(message, message.contains("ConfigurationProvider implementation with name 'doesNotExist'"));
            assertTrue(message, message.contains(ConfigurationProviderFactory.CONFIGURATION_PROVIDER));
            assertTrue(message, message.contains("Available: ["));
            assertTrue(message, message.contains("memory"));
            assertTrue(message, message.contains("FailingConfigurationProvider could not be instantiated"));
            assertTrue(message, message.contains("IllegalStateException: config provider constructor failed"));
        } finally {
            appender.detach();
        }
    }

    @Test
    public void nothingIsLoggedAtErrorWhenTheProviderIsFound() {
        CapturingLogAppender appender = CapturingLogAppender.attach(ConfigurationProviderFactory.class);
        try {
            new ConfigurationProviderFactory(settingsWithProvider("memory"), null).getConfigurationProvider();
            assertTrue(appender.events(Level.ERROR).isEmpty());
            assertTrue(appender.events(Level.WARN).isEmpty());
        } finally {
            appender.detach();
        }
    }
}
