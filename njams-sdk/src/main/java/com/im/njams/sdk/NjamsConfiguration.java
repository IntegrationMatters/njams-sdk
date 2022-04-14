package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.provider.FileConfigurationProvider;
import com.im.njams.sdk.settings.Settings;

public class NjamsConfiguration {

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;

    private final NjamsMetadata njamsMetadata;
    private final Sender sender;
    private final Settings njamsSettings;
    private final Configuration configuration;

    public NjamsConfiguration(NjamsMetadata njamsMetadata, Sender sender, Settings njamsSettings) {
        this.njamsMetadata = njamsMetadata;
        this.sender = sender;
        this.njamsSettings = njamsSettings;
        ConfigurationProvider provider = loadConfigurationProvider();
        this.configuration = getConfigurationFrom(provider);
    }

    /**
     * Load the ConfigurationProvider via the provided Properties
     */
    private ConfigurationProvider loadConfigurationProvider() {
        if (!njamsSettings.containsKey(ConfigurationProviderFactory.CONFIGURATION_PROVIDER)) {
            njamsSettings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, DEFAULT_CACHE_PROVIDER);
        }
        return new ConfigurationProviderFactory(njamsSettings).getConfigurationProvider();

    }

    /**
     * load and apply configuration from configuration provider
     * @param configurationProvider
     * @return
     */
    private Configuration getConfigurationFrom(ConfigurationProvider configurationProvider) {
        Configuration configuration = new Configuration();
        configuration.setConfigurationProvider(configurationProvider);
        njamsSettings.addSecureProperties(configurationProvider.getSecureProperties());
        return configurationProvider.loadConfiguration();
    }

    public void start() {
        NjamsDatamasking.initialize(njamsSettings, configuration);
        CleanTracepointsTask.start(njamsMetadata, configuration, sender);
    }

    public void stop() {
        CleanTracepointsTask.stop(njamsMetadata);
    }

    public LogMode getLogMode() {
        return configuration.getLogMode();
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
