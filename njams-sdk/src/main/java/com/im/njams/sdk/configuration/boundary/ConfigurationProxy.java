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
package com.im.njams.sdk.configuration.boundary;

import com.im.njams.sdk.service.NjamsService;

import java.util.Properties;

/**
 * This interface must be implemented for creating new ConfigurationProvider
 *
 * @author pnientiedt
 */
public interface ConfigurationProxy extends NjamsService, ServerInstructionSettings {

    /**
     * Configure the new ConfigurationProvider via given Properties and the Njams object
     *
     * @param properties Settings Properties
     */
    void configure(Properties properties);

    /**
     * This function return the Configuration in memory.
     * If no Configuration is available in memory,
     * it should be tried to load a Configuration of the underlying storage.
     * If the storage does not contain a Configuration, it should return a new
     * empty Configuration and safe it in memory as well.
     */
    void loadConfiguration();

    /**
     * This function should save the Configuration to the underlying storage.
     *
     */
    void saveConfiguration();

    /**
     * Returns the prefix for this ConfigurationProvider. Only properties
     * starting with this prefix will be given to the ConfigurationProvider for
     * initialization.
     *
     * @return the prefix
     */
    String getPropertyPrefix();
}
