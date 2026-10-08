package com.im.njams.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.provider.NoInitFilterConfigurationProvider;
import com.im.njams.sdk.settings.Settings;

/**
 * Verifies that settings-based process exclude patterns are applied by {@link Njams#start()}, independent of
 * whether the configuration provider initializes the process filter itself.
 */
public class NjamsConfigurationExcludePatternTest {

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    private Njams startNjams(String provider) {
        Settings settings = TestSender.getSettings();
        settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, provider);
        settings.put(NjamsSettings.PROPERTY_PROCESS_EXCLUDE_REGEX_PREFIX + "x", ">SDK4>TEST>excluded>.*");
        njams = new Njams(Path.of("SDK4", "TEST"), "TEST", "SDK4", settings);
        njams.start();
        return njams;
    }

    private void assertPatternApplied() {
        assertTrue(njams.configuration().isExcluded(Path.of("SDK4", "TEST", "excluded", "p")));
        assertFalse(njams.configuration().isExcluded(Path.of("SDK4", "TEST", "other", "p")));
    }

    @Test
    public void configurationLoadedDirectlyFromProviderAppliesSettingPatterns() {
        startNjams("memory");
        Configuration loaded = njams.configuration().get().getConfigurationProvider().loadConfiguration();
        assertTrue(loaded.isProcessExcluded(Path.of("SDK4", "TEST", "excluded", "p")));
        assertFalse(loaded.isProcessExcluded(Path.of("SDK4", "TEST", "other", "p")));
    }

    @Test
    public void builtInProviderAppliesSettingPatterns() {
        startNjams("memory");
        assertPatternApplied();
    }

    @Test
    public void providerNotInitializingFilterStillAppliesSettingPatterns() {
        startNjams(NoInitFilterConfigurationProvider.NAME);
        assertPatternApplied();
    }
}
