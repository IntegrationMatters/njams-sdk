/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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
