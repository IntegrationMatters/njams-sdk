package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.settings.SettingsProvider;

import java.util.Properties;

public class SettingsProviderFactory extends ServiceFactory{

    private static SettingsProvider globalSettingsProviderInstance;

    /**
     * Property key for the settings properties. Specifies which implementation will be loaded.
     */
    public static final String SETTINGS_PROVIDER = "njams.sdk.settings.provider";

    public SettingsProviderFactory(Properties properties) {
        super(properties.getProperty(SETTINGS_PROVIDER), SettingsProvider.class);
    }

    @Override
    public SettingsProvider getInstance() {
        if (globalSettingsProviderInstance == null) {
            globalSettingsProviderInstance = super.getInstance();
        }
        return globalSettingsProviderInstance;
    }
}
