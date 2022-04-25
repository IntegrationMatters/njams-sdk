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

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.settings.SettingsProvider;
import com.im.njams.sdk.settings.SettingsProviderFactory;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Implements a {@link SettingsProvider} that loads and saves settings to a
 * specified file in common {@link Properties} format.<br>
 * Allows recursively loading parent properties via key
 * {@value NjamsSettings#PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE}.
 *
 * @author cwinkler
 */
public class PropertiesFileSettingsProvider implements SettingsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesFileSettingsProvider.class);

    /**
     * Name of this implementation that has to be used to configure
     * {@link SettingsProviderFactory}.
     */
    public static final String NAME = "propertiesFile";
    /**
     * Default configuration file name.
     */
    public static final String DEFAULT_FILE = "config.properties";

    /**
     * Property key for specifying the path to the properties file to be used as
     * settings.
     */
    @Deprecated
    public static final String FILE_CONFIGURATION = "njams.sdk.settings.properties.file";
    /**
     * Default property key for loading parent (default) configuration file. See
     * {@link #PARENT_CONFIGURATION_KEY} for using an alternative key.
     */
    @Deprecated
    public static final String PARENT_CONFIGURATION = "njams.sdk.settings.properties.parent";
    /**
     * Allows to override the default parent file key
     * ({@value #PARENT_CONFIGURATION}).
     */
    @Deprecated
    public static final String PARENT_CONFIGURATION_KEY = "njams.sdk.settings.properties.parentKey";

    /**
     * The currently set properties file.
     */
    private File file = new File(DEFAULT_FILE);
    /**
     * The actual parent key to be used when loading the initial properties.
     */
    private String parentKey = NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE;

    private int circuitBreaker = 0;

    /**
     * Configures this FileSettingsProvider via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value NjamsSettings#PROPERTY_PROPERTIES_FILE_SETTINGS_FILE}
     * </ul>
     *
     * @param properties to configure
     */
    @Override
    public void configure(final Properties properties) {
        if (properties.containsKey(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE)) {
            String fileName = properties.getProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_FILE);
            if (StringUtils.isNotBlank(fileName)) {
                setFile(new File(fileName));
            }
        }
        parentKey = getParentKey(properties, NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_FILE);
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
     */
    @Override
    public Settings loadSettings() {
        //The file hasn't been set yet
        if (file == null) {
            throw new IllegalStateException("No properties file configured.");
        }
        //The file can't be found
        if (!file.exists()) {
            LOG.debug("The Propertyfile {} doesn't exists.", file);
            LOG.debug("Create empty Settings...");
            return new Settings();
        }
        Properties properties = loadProperties(file);

        LOG.info("Loaded settings from {}", file);
        final Settings settings = new Settings();
        properties.entrySet()
            .stream()
            .forEach(e -> settings.put((String) e.getKey(), (String) e.getValue()));
        return settings;
    }

    private Properties loadProperties(File currentFile) {
        //This shouldn't happen
        if (currentFile == null) {
            return new Properties();
        }
        //The file can't be found, recursion cancellation
        if (!currentFile.exists()) {
            LOG.debug("The Propertyfile {} doesn't exists.", currentFile);
            return new Properties();
        }

        //Get the java properties out of the file
        Properties currentProperties = new Properties();
        try (InputStream is = new FileInputStream(currentFile)) {
            currentProperties.load(is);
            LOG.debug("The Propertyfile {} has been loaded.", currentFile);
        } catch (final Exception e) {
            throw new NjamsSdkRuntimeException("Unable to load properties file " + currentFile.getPath(), e);
        }
        //Get the current Parent key and path to the file
        String currentParentKey = getParentKey(currentProperties, parentKey);
        LOG.trace("This is the current parent key that is used: ", currentParentKey);
        String parentPath = currentProperties.getProperty(currentParentKey);
        LOG.trace("Propertyfile: {}, ParentKey: {}, ParentPath:{}.", currentFile, currentParentKey, parentPath);

        Properties parentProps = new Properties();
        //parentPath == null if the property wasn't found in the currentProperties
        //parentPath is empty if the property was set without a value
        if (parentPath != null && !parentPath.isEmpty()) {
            File parentFile = new File(parentPath);
            if (!parentFile.isAbsolute()) {
                // if relative, use same folder as base
                parentFile = new File(file.getParent(), parentFile.getName());
            }
            //Get the Properties of its the currentfiles parents
            circuitBreaker = ++circuitBreaker;
            if (circuitBreaker > 100) {
                LOG.warn("Circuit breaker detected a circle in load of settings parents. Stop loading parents. Please" +
                    "check for circular dependencies in settings.");
            } else {
                parentProps = loadProperties(parentFile);
            }
        }

        //Set the current Properties to the parentproperties, override them if needed to
        for (Object key : currentProperties.keySet()) {
            parentProps.setProperty((String) key, currentProperties.getProperty((String) key));
        }

        return parentProps;
    }

    /**
     * Returns the parent file key that is to be used with the given properties.
     *
     * @param properties
     * @param def        The default to be returned, if no key is specified in the
     *                   given properties.
     * @return
     */
    private String getParentKey(Properties properties, String def) {
        String key = properties.getProperty(NjamsSettings.PROPERTY_PROPERTIES_FILE_SETTINGS_PARENT_KEY, def);
        return StringUtils.isBlank(key) ? def : key;
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
     * @param file the file to load from. If the given {@link File} is a folder,
     *             the {@value #DEFAULT_FILE} file is loaded from that folder. Must not be
     *             <code>null</code>.
     * @throws IllegalArgumentException If the given file does not exist, or the
     *                                  default configuration file does not exist in the given folder.
     * @throws NullPointerException     if the given file is <code>null</code>
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
