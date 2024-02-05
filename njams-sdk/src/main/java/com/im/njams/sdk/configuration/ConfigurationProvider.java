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

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import com.im.njams.sdk.Njams;

/**
 * This interface must be implemented for creating new ConfigurationProvider
 *
 * @author pnientiedt
 */
public interface ConfigurationProvider {

    /**
     * Should return the name by which the new ConfigurationProvider could be
     * identified.
     *
     * @return name
     */
    public String getName();

    /**
     * Configure the new ConfigurationProvider via given Properties and the Njams object
     *
     * @param properties Settings Properties
     * @param njams Njams instance
     */
    public void configure(Properties properties, Njams njams);

    /**
     * This function should load the Configuration from the underlying storage
     * of the new ConfigurationProvider. If the storage does not contain a
     * Configuration, it should return a new empty Configuration
     *
     * @return Configuration
     */
    public Configuration loadConfiguration();

    /**
     * This function should save the Configuration to the underlying storage.
     *
     * @param configuration Configuration to be saved
     */
    public void saveConfiguration(Configuration configuration);

    /**
     * Returns the prefix for this ConfigurationProvider. Only properties
     * starting with this prefix will be given to the ConfigurationProvider for
     * initialization.
     *
     * @return the prefix
     */
    public String getPropertyPrefix();

    /**
     * Configuration providers store secrets, such as passwords, etc.
     * They should return the name of any property here, that must not be printed to console or log files
     *
     * @return Collection of secure settings that must not be printed into the startup banner
     */
    default Set<String> getSecureProperties() {
        return Collections.emptySet();
    }

    /**
     * Has to create a new {@link ProcessConfiguration} initialized with defaults.
     * @return  A new pre-initialized {@link #newProcesConfiguration()}
     */
    public ProcessConfiguration newProcesConfiguration();

}
