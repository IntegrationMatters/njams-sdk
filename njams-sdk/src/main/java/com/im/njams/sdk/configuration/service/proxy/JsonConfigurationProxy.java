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
package com.im.njams.sdk.configuration.service.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.configuration.entity.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * ConfigurationProvider implementation which saves the configuration as a JSON file.
 *
 * @author pnientiedt
 */
public class JsonConfigurationProxy extends MemoryConfigurationProxy {

    private static final String PROPERTY_PREFIX = "njams.sdk.configuration.json";

    /**
     * Settings parameter for the file used by the JsonConfigurationProvider
     */
    public static final String JSON_CONFIGURATION = PROPERTY_PREFIX + ".file";

    /**
     * Name of the FileConfigurationProvider
     */
    public static final String JSON_NAME = "json";
    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;
    private File file;

    /**
     * Create the FileConfigurationProvider
     */
    public JsonConfigurationProxy() {
        file = new File("configuration.json");
        this.objectMapper = JsonSerializerFactory.getDefaultMapper();
        this.objectWriter = this.objectMapper.writer();
    }

    /**
     * Configures this FileSettingsProvider via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value #JSON_CONFIGURATION}
     * </ul>
     *
     * @param properties Properties
     */
    @Override
    public void configure(Properties properties) {
        super.configure(properties);
        if (properties.containsKey(JSON_CONFIGURATION)) {
            file = new File(properties.getProperty(JSON_CONFIGURATION));
        }
    }

    /**
     * Returns the value {@value #JSON_NAME} as name for this ConfigurationProxy
     *
     * @return the name of this ConfigurationProxy
     */
    @Override
    public String getName() {
        return JSON_NAME;
    }

    /**
     * Loads the configuration from the underlying storage. If there is no Configuration or no underlying storage,
     * it creates a new Configuration and stores it in memory.
     *
     * @return the Configuration
     */
    @Override
    public void loadConfiguration() {
        if (!file.exists()) {
            super.loadConfiguration();
        } else {
            try {
                this.realConfiguration = objectMapper.readValue(new FileInputStream(file), Configuration.class);
            } catch (Exception e) {
                throw new NjamsSdkRuntimeException("Unable to load file " + file.getAbsolutePath(), e);
            }
        }
    }
    /**
     * Save the Configuration to the configured File
     */
    @Override
    public void saveConfiguration() {
        super.saveConfiguration();
        try {
            objectWriter.writeValue(file, realConfiguration);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Unable to save file to" + file.getAbsolutePath(), e);
        }
    }

    /**
     * Get the file
     *
     * @return the file to the configuration
     */
    public File getFile() {
        return file;
    }

    /**
     * Set the file
     *
     * @param file for the configuration
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Returns the prefix for the FileConfigurationProvider. Only properties starting with this
     * prefix will be used for initialization.
     *
     * @return the prefix
     */
    @Override
    public String getPropertyPrefix() {
        return PROPERTY_PREFIX;
    }
}
