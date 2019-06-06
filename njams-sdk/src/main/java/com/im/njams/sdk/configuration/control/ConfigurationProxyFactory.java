package com.im.njams.sdk.configuration.control;

import com.im.njams.sdk.configuration.boundary.ConfigurationProxy;
import com.im.njams.sdk.service.factories.ServiceFactory;
import com.im.njams.sdk.settings.PropertyUtil;

import java.util.Properties;

public class ConfigurationProxyFactory extends ServiceFactory {

    /**
     * Key for Configuration Provider
     */
    public static final String CONFIGURATION_PROXY_SERVICE = "njams.sdk.configuration.proxy";

    private Properties properties;

    /**
     * Properties should contain a value for {@value #CONFIGURATION_PROXY_SERVICE}.
     * This value must match to the name of the ConfigurationProvider.
     *
     * @param properties Settings Properties
     */
    public ConfigurationProxyFactory(Properties properties) {
        super(properties.getProperty(CONFIGURATION_PROXY_SERVICE), ConfigurationProxy.class);
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
            ConfigurationProxy configurationProxyInstance = super.getInstance();
            configurationProxyInstance.configure(PropertyUtil.filter(properties, configurationProxyInstance.getPropertyPrefix()));
        return configurationProxyInstance;
    }
}
