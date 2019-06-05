package com.im.njams.sdk.configuration.service.factory;

import com.im.njams.sdk.configuration.service.proxy.ConfigurationProxy;
import com.im.njams.sdk.service.factories.ServiceFactory;
import com.im.njams.sdk.settings.PropertyUtil;

import java.util.Properties;

public class ConfigurationProxyFactory extends ServiceFactory {

    /**
     * Key for Configuration Provider
     */
    public static final String CONFIGURATION_PROXY = "njams.sdk.configuration.proxy";

    private Properties properties;

    private static ConfigurationProxy globalConfigurationProxyInstance;

    /**
     * Properties should contain a value for {@value #CONFIGURATION_PROXY}.
     * This value must match to the name of the ConfigurationProvider.
     *
     * @param properties Settings Properties
     */
    public ConfigurationProxyFactory(Properties properties) {
        super(properties.getProperty(CONFIGURATION_PROXY), ConfigurationProxy.class);
        this.properties = properties;
    }

    /**
     * Returns the ConfigurationProxy, which name matches the name given via
     * the Properties into the constructor.
     *
     * @return Configuration Proxy matching CONFIGURATION_PROXY
     */
    @Override
    public ConfigurationProxy getInstance(){
        if(globalConfigurationProxyInstance == null){
            globalConfigurationProxyInstance = super.getInstance();
            globalConfigurationProxyInstance.configure(PropertyUtil.filter(properties, globalConfigurationProxyInstance.getPropertyPrefix()));
        }
        return globalConfigurationProxyInstance;
    }
}
