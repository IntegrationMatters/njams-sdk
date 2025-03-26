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
package com.im.njams.sdk.utils;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.settings.encoding.Transformer;

/**
 * Property Util
 *
 * @author pnientiedt
 */
public class PropertyUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyUtil.class);

    private PropertyUtil() {
        // static only
    }

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix.
     *
     * @param properties properties to be filtered
     * @param prefix prefix
     * @return new filtered Properties
     */
    public static Properties filter(Properties properties, String prefix) {
        Properties response = new Properties();
        properties.entrySet()
            .stream()
            .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
            .filter(e -> ((String) e.getKey()).startsWith(prefix))
            .forEach(e -> response.setProperty((String) e.getKey(), (String) e.getValue()));
        return response;
    }

    /**
     * Return new Properties, which contains only the properties starting with a
     * given prefix, stripped from that prefix.
     *
     * @param properties properties to be filtered
     * @param prefix prefix
     * @return new filtered and stripped Properties
     */
    public static Properties filterAndCut(Properties properties, String prefix) {
        Properties response = new Properties();
        properties.entrySet()
            .stream()
            .filter(e -> String.class.isAssignableFrom(e.getKey().getClass()))
            .filter(e -> ((String) e.getKey()).startsWith(prefix))
            .forEach(e -> response.setProperty(
                ((String) e.getKey()).substring(((String) e.getKey()).indexOf(prefix) + prefix.length()),
                (String) e.getValue()));
        return response;
    }

    /**
     * Returns the value for the given key from the given {@link Properties} and converts it to an integer.
     * @param properties The {@link Properties} to read from.
     * @param key The key to read from the properties.
     * @param defaultValue The default value to be used either when the key does not exists or the value could
     * not be converted to an integer.
     * @return The property's value as integer.
     */
    public static int getPropertyInt(Properties properties, String key, int defaultValue) {
        final String s = properties.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                LOG.warn("Failed to parse value {} of property {} to int.", s, key);
            }
        }
        return defaultValue;
    }

    /**
     * Returns the value for the given key from the given {@link Properties} and converts it to a <code>long</code> value.
     * @param properties The {@link Properties} to read from.
     * @param key The key to read from the properties.
     * @param defaultValue The default value to be used either when the key does not exists or the value could
     * not be converted to a <code>long</code>.
     * @return The property's value as <code>long</code>.
     */
    public static long getPropertyLong(Properties properties, String key, long defaultValue) {
        final String s = properties.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                LOG.warn("Failed to parse value {} of property {} to long.", s, key);
            }
        }
        return defaultValue;
    }

    /**
     * Returns the value for the given key from the given {@link Properties} and converts it to a boolean.
     * @param properties The {@link Properties} to read from.
     * @param key The key to read from the properties.
     * @param defaultValue The default value to be used when the key does not exists.
     * @return The property's value as boolean. Any string different from <code>true</code> (ignoring case) will
     * return <code>false</code>.
     */
    public static boolean getPropertyBool(Properties properties, String key, boolean defaultValue) {
        final String s = properties.getProperty(key);
        if (StringUtils.isNotBlank(s)) {
            return "true".equalsIgnoreCase(s);
        }
        return defaultValue;
    }

    /**
     * Same #as {@link #getPropertyWithDeprecationWarning(Properties, String, String, String)} using <code>null</code>
     * as default value.
     * @param properties The {@link Properties} to read from.
     * @param expectedKey The actually expected key.
     * @param deprecatedKey The deprecated key to try in case the <code>expectedKey</code> does not exists.
     * @return The value associated with either key, or <code>null</code> if not found.
     * @return
     */
    public static String getPropertyWithDeprecationWarning(Properties properties, String expectedKey,
        String deprecatedKey) {
        return getPropertyWithDeprecationWarning(properties, expectedKey, null, deprecatedKey);
    }

    /**
     * Tries to get a value from the given properties first using the <code>expectedKey</code>. If not found,
     * the <code>deprecatedKey</code> is tried. If still not found, the given <code>defaultValue</code> is returned.
     * <br>
     * If a value was found using the <code>deprecatedKey</code>, an according deprecation warning is logged.
     * @param properties The {@link Properties} to read from.
     * @param expectedKey The actually expected key.
     * @param defaultValue The default value to return if the neither the <code>expectedKey</code> nor the
     * <code>deprecatedKey</code> was found.
     * @param deprecatedKey The deprecated key to try in case the <code>expectedKey</code> does not exists.
     * @return The value associated with either key, or the given default.
     */
    public static String getPropertyWithDeprecationWarning(Properties properties, String expectedKey,
        String defaultValue, String deprecatedKey) {
        if (properties.containsKey(expectedKey)) {
            return Transformer.decode(properties.getProperty(expectedKey));
        }
        if (properties.containsKey(deprecatedKey)) {
            LOG.warn("Setting [{}] is outdated. Use [{}] instead.", deprecatedKey, expectedKey);
            return Transformer.decode(properties.getProperty(deprecatedKey));
        }
        return defaultValue;
    }
}
