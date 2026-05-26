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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.settings.encoding.Transformer;
import com.im.njams.sdk.utils.PropertyUtil;
import com.im.njams.sdk.utils.StringUtils;

/**
 * The settings contains settings needed for
 * {@link com.im.njams.sdk.Njams}
 * <p>
 * All static final names of settings are moved to @{@link com.im.njams.sdk.NjamsSettings}
 * Please only use them and not the deprecated ones from here-
 *
 * @author bwand
 * @deprecated This class is preserved only as a legacy implementation of {@link ClientSettings}
 *     for backwards compatibility with existing client code. The SDK itself no longer uses it
 *     internally — all internal settings handling now goes through the {@link ClientSettings}
 *     and {@link ReadOnlyClientSettings} interfaces. Obtain settings instances via one of the factory
 *     methods on those interfaces: {@link ClientSettings#from(java.util.Map)},
 *     {@link ClientSettings#from(java.util.Properties)},
 *     {@link ClientSettings#fromSystemProperties(java.util.function.Predicate)}, or
 *     {@link ReadOnlyClientSettings#fromEnvironment(java.util.function.Predicate)} for a read-only
 *     environment-backed view.
 */
@Deprecated
public class Settings implements ClientSettings {

    //The Logger
    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);

    private Properties properties;

    /**
     * all properties registered here are masked and not printed during startup phase
     * always use lowercase when adding an entry to this set!
     */
    private Set<String> secureProperties = new HashSet<>();

    /**
     * Property added internally for passing an instance's client path to the communication layer.
     */
    public static final String INTERNAL_PROPERTY_CLIENTPATH = "njams.$clientPath";

    public Settings() {
        properties = new Properties();
        secureProperties.add("password");
        secureProperties.add("credentials");
        secureProperties.add("secret");
        secureProperties.add("keystore.key");
    }

    /**
     * Return decoded property or null if not found
     *
     * @param key to look for
     * @return the found setting value
     */
    @Override
    public String getProperty(String key) {
        return Transformer.decode(properties.getProperty(key));
    }

    /**
     * * Return decoded property or default value if not found
     *
     * @param key          to look for
     * @param defaultValue which is returned if not found
     * @return the value.
     */
    public String getProperty(String key, String defaultValue) {
        return Transformer.decode(properties.getProperty(key, defaultValue));
    }

    /**
     * Check if key is found
     *
     * @param key to check
     * @return true if found else false
     */
    @Override
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    /**
     * Returns the set of property keys registered in these settings.
     *
     * @return an unmodifiable snapshot of the registered keys
     */
    @Override
    public Set<String> keySet() {
        return properties.stringPropertyNames();
    }

    /**
     * Put a key/value pair to settings.
     *
     * @param key   the key
     * @param value the value
     */
    @Override
    public void put(String key, String value) {
        properties.put(key, value);
    }

    /**
     * Stores all entries from the given map, replacing any previous values for the same keys.
     *
     * @param entries the entries to add
     */
    @Override
    public void putAll(Map<String, String> entries) {
        properties.putAll(entries);
    }

    /**
     * Returns an iterator over a snapshot of the currently registered key/value pairs. Values are
     * decoded before being returned. The returned iterator does not support removal.
     *
     * @return an iterator over the current entries
     */
    @Override
    public Iterator<Entry<String, String>> iterator() {
        return properties.stringPropertyNames().stream()
            .map(key -> Map.entry(key, Transformer.decode(properties.getProperty(key))))
            .iterator();
    }

    /**
     * Reset the settings, which means everything will be deleted.
     */
    public void reset() {
        properties = new Properties();
    }

    /**
     * This method prints all Properties, but the values of all keys that contains
     * "password" or "credentials" are changed to "****".
     */
    public void printPropertiesWithoutPasswords() {
        printPropertiesWithoutPasswords(LOG);
    }

    /**
     * This method prints all properties to the given logger, but the values of all keys that contain
     * "password" or "credentials" are changed to "****".
     *
     * @param logger The logger used for printing properties.
     */
    @Override
    public void printPropertiesWithoutPasswords(Logger logger) {
        List<String> list = new ArrayList<>();
        properties.keySet().forEach(key -> list.add((String) key));
        Collections.sort(list);
        list.forEach(key -> {
            if (isSecuredKey(key)) {
                logger.info("***      {} = ****", key);
            } else {
                logger.info("***      {} = {}", key, properties.getProperty(key));
            }
        });
    }

    /**
     * Returns an unmodifiable view of the registered secured-key tokens. A property key whose
     * lowercased form contains any of these tokens is considered secured.
     *
     * @return the secured-key tokens
     */
    @Override
    public Set<String> getSecuredProperties() {
        return Collections.unmodifiableSet(secureProperties);
    }

    /**
     * Returns <code>true</code> if the value for the given key is private and must be shown in the logs (e.g.,
     * passwords, etc).
     *
     * @param key The properties key to test.
     * @return <code>true</code> if the value for the given key must not be shown.
     */
    public boolean isSecuredKey(final String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        final String lowerKey = key.toLowerCase();
        return secureProperties.stream().anyMatch(s -> lowerKey.contains(s));
    }

    /**
     * Return all Properties.They will be decoded because user cannot know which ones are encoded.
     *
     * @return the properties.
     */
    public Properties getAllProperties() {
        return PropertyUtil.toProperties(this);
    }

    /**
     * Return Properties, which contains only the properties starting with a given prefix.
     *
     * @param prefix prefix
     * @return new filtered Properties
     */
    public Properties filter(String prefix) {
        return PropertyUtil.toProperties(filteredStream(prefix, false));
    }

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix, stripped from that prefix.
     *
     * @param prefix prefix
     * @return new filtered and stripped Properties
     */
    public Properties filterAndCut(String prefix) {
        return PropertyUtil.toProperties(filteredStream(prefix, true));
    }

    public void addAll(Properties properties) {
        this.properties.putAll(properties);
    }

    @Override
    public void addSecureProperties(Set<String> secureProperties) {
        secureProperties.forEach(property -> {
            this.secureProperties.add(property.toLowerCase());
        });
    }

    /**
     * Same as {@link #getPropertyWithDeprecationWarning(String, String, String)} with <code>null</code> as default value.
     * @param expectedKey The expected (current) key.
     * @param deprecatedKey Deprecated key to try if the expected one does not exist.
     * @return see {@link #getPropertyWithDeprecationWarning(String, String, String)}
     */
    @Override
    public String getPropertyWithDeprecationWarning(String expectedKey, String deprecatedKey) {
        return getPropertyWithDeprecationWarning(expectedKey, null, deprecatedKey);
    }

    /**
     * Same as {@link #getProperty(String, String)} if the <code>expectedKey</code> exists.
     * If not, the <code>deprecatedKey</code> is tried and if found, a deprecation warning is logged for that key
     * and the value is returned.
     * Only if the <code>deprecatedKey</code> was also not found, the <code>default</code> value is returned.
     * @param expectedKey The expected (current) key.
     * @param defaultValue The default to return in case that no key exists at all.
     * @param deprecatedKey Deprecated key to try if the expected one does not exist.
     * @return A value for the given keys as explained above.
     */
    @Override
    public String getPropertyWithDeprecationWarning(String expectedKey, String defaultValue, String deprecatedKey) {
        return PropertyUtil.getPropertyWithDeprecationWarning(getAllProperties(), expectedKey, defaultValue,
            deprecatedKey);
    }

}
