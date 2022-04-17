package com.im.njams.sdk.njams;

import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.provider.FileConfigurationProvider;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;

public class NjamsConfigurationFactory {

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;

    public static NjamsConfiguration getNjamsConfiguration(NjamsMetadata njamsMetadata, NjamsSender njamsSender, Settings settings) {
        ConfigurationProvider provider = loadConfigurationProvider(settings);
        final Configuration configuration = getConfigurationFrom(provider, settings);

        return new NjamsConfiguration(configuration, njamsMetadata, njamsSender, settings);
    }

    /**
     * Load the ConfigurationProvider via the provided Properties
     * @param njamsSettings
     */
    private static ConfigurationProvider loadConfigurationProvider(Settings njamsSettings) {
        if (!njamsSettings.containsKey(ConfigurationProviderFactory.CONFIGURATION_PROVIDER)) {
            njamsSettings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, DEFAULT_CACHE_PROVIDER);
        }
        return new ConfigurationProviderFactory(njamsSettings).getConfigurationProvider();

    }

    /**
     * load and apply configuration from configuration provider
     * @param configurationProvider
     * @param njamsSettings
     * @return
     */
    private static Configuration getConfigurationFrom(ConfigurationProvider configurationProvider, Settings njamsSettings) {
        Configuration configuration = new Configuration();
        configuration.setConfigurationProvider(configurationProvider);
        njamsSettings.addSecureProperties(configurationProvider.getSecureProperties());
        return configurationProvider.loadConfiguration();
    }
}
