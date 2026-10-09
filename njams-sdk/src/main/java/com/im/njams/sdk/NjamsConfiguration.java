/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.provider.FileConfigurationProvider;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns the server-driven runtime {@link Configuration} of an {@link Njams} client
 * (log mode, process exclusions, tracepoints) and its provider, and the client's {@link DataMasker}.
 * Obtain via {@code njams.configuration()}.
 */
public class NjamsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsConfiguration.class);

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;

    private final ClientSettings settings;
    private final DataMasker dataMasker = new DataMasker(true);
    private Configuration configuration;

    NjamsConfiguration(ClientSettings settings, Njams njams) {
        this.settings = settings;
        loadConfigurationProvider(njams);
    }

    /**
     * Returns the runtime configuration received from the nJAMS server.
     *
     * @return the configuration, never <code>null</code>
     */
    public Configuration get() {
        return configuration;
    }

    /**
     * Returns the data masking of this client instance. It masks with the patterns from this instance's settings
     * and configuration, applied on every start, and with patterns client code adds to it.
     *
     * @return the data masker of this instance, never <code>null</code>
     */
    public DataMasker dataMasking() {
        return dataMasker;
    }

    /**
     * Returns the log mode of this client.
     *
     * @return LogMode of this client
     */
    public LogMode getLogMode() {
        return configuration.getLogMode();
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isExcluded(Path processPath) {
        return configuration.isProcessExcluded(processPath);
    }

    /**
     * Load the ConfigurationProvider via the provided settings.
     */
    private void loadConfigurationProvider(Njams njams) {
        if (!settings.containsKey(ConfigurationProviderFactory.CONFIGURATION_PROVIDER)) {
            settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, DEFAULT_CACHE_PROVIDER);
        }
        ConfigurationProvider configurationProvider = new ConfigurationProviderFactory(settings, njams)
            .getConfigurationProvider();
        configuration = new Configuration();
        configuration.setConfigurationProvider(configurationProvider);
        settings.addSecureProperties(configurationProvider.getSecureProperties());
    }

    /**
     * Load and apply configuration from the configuration provider; called by Njams.start().
     */
    void load() {
        ConfigurationProvider configurationProvider = configuration.getConfigurationProvider();
        if (configurationProvider != null) {
            configuration = configurationProvider.loadConfiguration();
        }
    }

    /**
     * Applies the masking patterns from the settings and the configuration; called by Njams.start().
     */
    void initializeDataMasking() {
        if (!settings.getBool(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, true)) {
            LOG.info("DataMasking is disabled.");
            dataMasker.clearConfiguredPatterns();
            return;
        }
        final List<String> fromConfiguration = configuration.getDataMasking();
        if (!fromConfiguration.isEmpty()) {
            LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                    "with the properties \n{} = " +
                    "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
                NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX);
        }
        dataMasker.replaceConfiguredPatterns(settings, fromConfiguration);
    }
}
