/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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

import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.provider.AbstractConfigurationProvider;
import com.im.njams.sdk.configuration.provider.ConfigurationValidationResult;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.ServiceLoaderSupport;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Creates the ConfigurationProvider, which has been specified in the settings
 * properties;
 *
 * @author pnientiedt
 */
public class ConfigurationProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationProviderFactory.class);

    /**
     * Key for Configuration Provider
     */
    public static final String CONFIGURATION_PROVIDER = "njams.sdk.configuration.provider";

    private Settings settings;
    private final Njams njams;

    /**
     * Properties should contain a value for {@value #CONFIGURATION_PROVIDER}.
     * This value must match to the name of the ConfigurationProvider.
     *
     * @param settings Settings
     * @param njams Njams instance
     */
    public ConfigurationProviderFactory(Settings settings, Njams njams) {
        this.settings = settings;
        this.njams = njams;
    }

    /**
     * Returns the ConfigurationProvider, which name matches the name given via
     * the Properties into the constructor.
     *
     * @return Configuration Provider matching CONFIGURATION_PROVIDER
     */
    public ConfigurationProvider getConfigurationProvider() {
        final String name = settings.getProperty(CONFIGURATION_PROVIDER);
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(
                    "Unable to find " + CONFIGURATION_PROVIDER + " in configuration properties");
        }
        final ServiceLoaderSupport<ConfigurationProvider> serviceLoader =
                new ServiceLoaderSupport<>(ConfigurationProvider.class);
        final ConfigurationProvider configurationProvider = serviceLoader.find(s -> name.equals(s.getName()));
        if (configurationProvider != null) {
            LOG.info("Create ConfigurationProvider {}", configurationProvider.getName());
            configurationProvider.configure(createProviderProperties(configurationProvider), njams);
            validate(configurationProvider);
            return configurationProvider;
        }
        throw new IllegalArgumentException(
                "Unable to find ConfigurationProvider implementation with name " + name + ", available are: "
                        + serviceLoader.stream().map(ConfigurationProvider::getName).sorted()
                                .collect(Collectors.toList()));
    }

    private void validate(ConfigurationProvider cfg) {
        final ConfigurationValidationResult result = cfg.validate();
        if (result == null || result.isWritable()) {
            return;
        }
        final String errors =
                result.hasErrors() ? result.getErrors().stream().map(Object::toString).collect(Collectors.joining(", "))
                        : "";
        if (!result.isReadable()) {
            throw new IllegalStateException("Configuration is not readable: " + errors);
        }
        LOG.warn("Configuration is not writable! Configuration updates cannot be persisted: {}", errors);

    }

    private Properties createProviderProperties(ConfigurationProvider configurationProvider) {
        final Properties props = settings.filter(configurationProvider.getPropertyPrefix());
        if (configurationProvider instanceof AbstractConfigurationProvider) {
            if (settings.containsKey(NjamsSettings.PROPERTY_BOOSTRAP_RECORDING)) {
                props.put(AbstractConfigurationProvider.DEFAULT_RECORDING_CONFIG,
                        settings.getProperty(NjamsSettings.PROPERTY_BOOSTRAP_RECORDING));
            }
            if (settings.containsKey(NjamsSettings.PROPERTY_LOG_MODE_DEFAULT)) {
                props.put(AbstractConfigurationProvider.DEFAULT_LOG_MODE_CONFIG,
                        settings.getProperty(NjamsSettings.PROPERTY_LOG_MODE_DEFAULT));
            }
            if (settings.containsKey(NjamsSettings.PROPERTY_LOG_LEVEL_DEFAULT)) {
                props.put(AbstractConfigurationProvider.DEFAULT_LOG_LEVEL_CONFIG,
                        settings.getProperty(NjamsSettings.PROPERTY_LOG_LEVEL_DEFAULT));
            }
        }
        return props;
    }
}
