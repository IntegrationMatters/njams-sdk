package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.settings.SettingsProvider;

import java.util.Properties;

public class SettingsProxyFactory extends ServiceFactory{

    private static SettingsProvider globalSettingsProviderInstance;

    /**
     * Property key for the settings properties. Specifies which implementation will be loaded.
     */
    public static final String SETTINGS_PROXY = "njams.sdk.settings.proxy";

    //Todo: check if this constructor is necessary
    public SettingsProxyFactory(Properties properties) {
        super(properties.getProperty(SETTINGS_PROXY), SettingsProvider.class);
    }

    public SettingsProxyFactory(String providerFactoryServiceName){
        super(providerFactoryServiceName, SettingsProvider.class);
    }

    @Override
    public SettingsProvider getInstance() {
        if (globalSettingsProviderInstance == null) {
            globalSettingsProviderInstance = super.getInstance();
        }
        return globalSettingsProviderInstance;
    }
}
