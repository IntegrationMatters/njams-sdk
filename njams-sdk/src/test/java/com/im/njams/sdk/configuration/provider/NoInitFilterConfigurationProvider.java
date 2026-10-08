package com.im.njams.sdk.configuration.provider;

import com.im.njams.sdk.configuration.Configuration;

/**
 * Test provider that, like a custom SPI implementation might, returns a configuration without ever calling
 * {@link Configuration#initFilter(com.im.njams.sdk.settings.ClientSettings)}.
 */
public class NoInitFilterConfigurationProvider extends AbstractConfigurationProvider {

    public static final String NAME = "noInitFilter";

    private Configuration configuration;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Configuration loadConfiguration() {
        if (configuration == null) {
            configuration = new Configuration();
            configuration.setConfigurationProvider(this);
        }
        return configuration;
    }

    @Override
    public void saveConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getPropertyPrefix() {
        return "njams.sdk.configuration.noinitfilter";
    }
}
