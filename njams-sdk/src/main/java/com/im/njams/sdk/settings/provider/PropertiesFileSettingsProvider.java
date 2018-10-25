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
package com.im.njams.sdk.settings.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.SettingsProvider;
import com.im.njams.sdk.settings.SettingsProviderFactory;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Implements a {@link SettingsProvider} that loads and saves settings to a specified file in common {@link Properties}
 * format.<br>
 * Allows recursively loading parent (default) properties via key {@value #PARENT_CONFIGURATION}.
 *
 * @author cwinkler
 */
public class PropertiesFileSettingsProvider implements SettingsProvider {

    private static class ExtProperties extends Properties {
        private static final long serialVersionUID = -2671747945697855932L;

        /**
         * Make protected defaults in {@link Properties} accessible here.
         * @param defaults
         */
        private void setDefaults(final Properties defaults) {
            this.defaults = defaults;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesFileSettingsProvider.class);

    /** Name of this implementation that has to be used to configure {@link SettingsProviderFactory}. */
    public static final String NAME = "propertiesFile";
    /** Default configuration file name. */
    public static final String DEFAULT_FILE = "config.properties";

    /** Property key for specifying the path to the properties file to be used as settings. */
    public static final String FILE_CONFIGURATION = "njams.sdk.settings.properties.file";
    /** Default property key for loading parent (default) configuration file.
     *  See {@link #PARENT_CONFIGURATION_KEY} for using an alternative key. */
    public static final String PARENT_CONFIGURATION = "njams.sdk.settings.properties.parent";
    /** Allows to override the default parent file key ({@value #PARENT_CONFIGURATION}). */
    public static final String PARENT_CONFIGURATION_KEY = "njams.sdk.settings.properties.parentKey";

    /** The currently set properties file. */
    private File file = new File(DEFAULT_FILE);
    /** The actual parent key to be used when loading the initial properties. */
    private String parentKey = PARENT_CONFIGURATION;

    /**
     * Configures this FileSettingsProvider via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value #FILE_CONFIGURATION}
     * </ul>
     *
     * @param properties to configure
     */
    @Override
    public void configure(final Properties properties) {
        if (properties.containsKey(FILE_CONFIGURATION)) {
            String fileName = properties.getProperty(FILE_CONFIGURATION);
            if (StringUtils.isNotBlank(fileName)) {
                setFile(new File(fileName));
            }
        }
        parentKey = getParentKey(properties, PARENT_CONFIGURATION);
    }

    /**
     * Returns the value {@value #NAME} as name for this SettingsProvider.
     *
     * @return the name of this SettingsProvider
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Loads the Settings from the configured file, or returns a new empty
     * Settings if the file does not exist.
     *
     * @return the {@link Settings}
     * @throws NjamsSdkRuntimeException if the file could not be loaded.
     */
    @Override
    public Settings loadSettings() {
        if (file == null) {
            throw new IllegalStateException("No properties file configured.");
        }

        if (!file.exists()) {
            return new Settings();
        }

        final ExtProperties properties = new ExtProperties();
        try (InputStream is = new FileInputStream(file)) {
            properties.load(is);
            LOG.info("Loaded settings from {}", file);

            loadParent(getParentKey(properties, parentKey), properties);

        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to load properties file", e);
        }

        final Settings settings = new Settings();
        settings.setProperties(properties);
        return settings;
    }

    /**
     * Loads defaults for the given properties if a parent file is configured in that properties.
     * @param parentKey
     * @param currentProps
     */
    private void loadParent(final String parentKey, final ExtProperties currentProps) {
        String parentFileKey = getParentKey(currentProps, parentKey);
        if (!currentProps.containsKey(parentFileKey)) {
            return;
        }

        File parentFile = new File(currentProps.getProperty(parentFileKey));
        if (!parentFile.isAbsolute()) {
            // if relative, use same folder as base
            parentFile = new File(file.getParent(), parentFile.getName());
        }
        final ExtProperties parentProps = new ExtProperties();
        try (InputStream is = new FileInputStream(parentFile)) {
            parentProps.load(is);
        } catch (final Exception e) {
            LOG.error("Unable to load parent properties file {}", parentFile, e);
            return;
        }
        LOG.info("Loaded parent settings from {}", parentFile);
        currentProps.setDefaults(parentProps);
        // recursive call for loading parent's parents
        loadParent(parentKey, parentProps);
    }

    /**
     * Returns the parent file key that is to be used with the given properties.
     * @param properties
     * @param def The default to be returned, if no key is specified in the given properties.
     * @return
     */
    private String getParentKey(Properties properties, String def) {
        String key = properties.getProperty(PARENT_CONFIGURATION_KEY, def);
        return StringUtils.isBlank(key) ? def : key;
    }

    /**
     * Save the given Settings to the configured File.
     *
     * @param settings to be saved
     * @throws NjamsSdkRuntimeException if the file could not be saved.
     */
    @Override
    public void saveSettings(final Settings settings) {
        if (settings == null || settings.getProperties() == null) {
            return;
        }
        if (file == null) {
            throw new IllegalStateException("No properties file configured.");
        }
        try (OutputStream os = new FileOutputStream(file)) {
            settings.getProperties().store(os, null);
        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to save properties file", e);
        }
    }

    /**
     * Get the {@link File} where {@link Settings} are stored.
     *
     * @return the currently set file, which may be a default.
     */
    public File getFile() {
        return file;
    }

    /**
     * Set the {@link File} from where {@link Settings} should be loaded.
     *
     * @param file the file to load from. If the given {@link File} is a folder, the {@value #DEFAULT_FILE} file is
     * loaded from that folder. Must not be <code>null</code>.
     * @throws IllegalArgumentException If the given file does not exist, or the default configuration file does not
     * exist in the given folder.
     * @throws NullPointerException if the given file is <code>null</code>
     */
    public void setFile(final File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("Configuration file " + file + " not exists.");
        }
        if (file.isDirectory()) {
            File configFile = new File(file, DEFAULT_FILE);
            if (!configFile.exists()) {
                throw new IllegalArgumentException("Configuration file " + configFile + " not exists.");
            }
            this.file = configFile;
        } else {
            this.file = file;
        }
        LOG.debug("Set config file to {}", this.file);
    }

}
