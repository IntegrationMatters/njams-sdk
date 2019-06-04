package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication_rework.instruction.entity.ConfigurationProvider;
import com.im.njams.sdk.settings.PropertyUtil;

import java.util.Properties;

public class ConfigurationProviderFactory extends ServiceFactory{

    /**
     * Key for Configuration Provider
     */
    public static final String CONFIGURATION_PROVIDER = "njams.sdk.configuration.provider";

    private Properties properties;
    private final Njams njams;

    private ConfigurationProvider configurationProviderInstance;

    /**
     * Properties should contain a value for {@value #CONFIGURATION_PROVIDER}.
     * This value must match to the name of the ConfigurationProvider.
     *
     * @param properties Settings Properties
     * @param njams Njams instance
     */
    public ConfigurationProviderFactory(Properties properties, Njams njams) {
        super(properties.getProperty(CONFIGURATION_PROVIDER), ConfigurationProvider.class);
        this.properties = properties;
        this.njams = njams;
        this.configurationProviderInstance = null;
    }

    /**
     * Returns the ConfigurationProvider, which name matches the name given via
     * the Properties into the constructor.
     *
     * @return Configuration Provider matching CONFIGURATION_PROVIDER
     */
    @Override
    public ConfigurationProvider getInstance(){
        if(configurationProviderInstance == null){
            configurationProviderInstance = super.getInstance();
            configurationProviderInstance.configure(PropertyUtil.filter(properties, configurationProviderInstance.getPropertyPrefix()), njams);
        }
        return configurationProviderInstance;
    }
}
