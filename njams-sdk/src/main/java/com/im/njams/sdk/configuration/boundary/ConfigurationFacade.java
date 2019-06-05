package com.im.njams.sdk.configuration.boundary;

import com.im.njams.sdk.configuration.control.ConfigurationProxy;
import com.im.njams.sdk.configuration.control.ConfigurationProxyFactory;
import com.im.njams.sdk.configuration.control.JsonConfigurationProxy;

import java.util.Properties;

public class ConfigurationFacade {

    private static final String DEFAULT_CONFIGURATION_FACTORY = JsonConfigurationProxy.JSON_NAME;

    private ConfigurationProxyFactory configurationFactory;

    public ConfigurationFacade(Properties properties){
        this.configurationFactory = createConfigurationProxyFactory(properties);
    }

    private ConfigurationProxyFactory createConfigurationProxyFactory(Properties properties) {
        setDefaultProxyFactoryIfNecessary(ConfigurationProxyFactory.CONFIGURATION_PROXY, DEFAULT_CONFIGURATION_FACTORY, properties);
        return new ConfigurationProxyFactory(properties);
    }

    private void setDefaultProxyFactoryIfNecessary(String defaultConfigurationProxyKey, String defaultConfigurationProxyValue, Properties properties) {
        if (!properties.containsKey(defaultConfigurationProxyKey)) {
            properties.put(defaultConfigurationProxyKey, defaultConfigurationProxyValue);
        }
    }

    public void start(){
        ((ConfigurationProxy)getConfiguration()).loadConfiguration();
    }

    public ServerInstructionSettings getConfiguration(){
        return configurationFactory.getInstance();
    }
}
