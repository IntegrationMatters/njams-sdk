/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.configuration;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the ConfigurationProvider, which has been specified in the settings
 * properties;
 */
public class ConfigurationProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationProviderFactory.class);

    /**
     * Key for Configuration Provider
     */
    public static final String CONFIGURATION_PROVIDER = "njams.sdk.configuration.provider";

    private Settings settings;

    /**
     * Properties should contain a value for {@value #CONFIGURATION_PROVIDER}.
     * This value must match to the name of the ConfigurationProvider.
     *
     * @param settings Settings
     */
    public ConfigurationProviderFactory(Settings settings) {
        this.settings = settings;
    }

    /**
     * Returns the ConfigurationProvider, which name matches the name given via
     * the Properties into the constructor.
     *
     * @return Configuration Provider matching CONFIGURATION_PROVIDER
     */
    public ConfigurationProvider getConfigurationProvider() {
        if (settings.containsKey(CONFIGURATION_PROVIDER)) {
            String name = settings.getProperty(CONFIGURATION_PROVIDER);
            ServiceLoader<ConfigurationProvider> receiverList = ServiceLoader.load(ConfigurationProvider.class);
            Iterator<ConfigurationProvider> iterator = receiverList.iterator();
            while (iterator.hasNext()) {
                ConfigurationProvider configurationProvider = iterator.next();
                if (configurationProvider.getName().equals(name)) {
                    LOG.info("Create ConfigurationProvider {}", configurationProvider.getName());
                    configurationProvider.configure(settings.filter(configurationProvider.getPropertyPrefix()));
                    return configurationProvider;
                }
            }
            String available = StreamSupport.stream(Spliterators.spliteratorUnknownSize(ServiceLoader.load(ConfigurationProvider.class).iterator(),
                    Spliterator.ORDERED), false)
                    .map(cp -> cp.getName()).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException("Unable to find ConfigurationProvider implementation with name " + name + ", available are: " + available);
        } else {
            throw new UnsupportedOperationException("Unable to find " + CONFIGURATION_PROVIDER + " in configuration properties");
        }
    }
}
