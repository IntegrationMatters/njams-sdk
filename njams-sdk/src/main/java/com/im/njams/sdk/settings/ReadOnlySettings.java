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
package com.im.njams.sdk.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only view of the nJAMS SDK settings. Exposes only operations that retrieve or report
 * configuration values without mutating them. Implementations are {@link Iterable} over the
 * registered key/value pairs.
 */
public interface ReadOnlySettings extends Iterable<Entry<String, String>> {

    /**
     * Returns a {@link ReadOnlySettings} backed by the given map. Subsequent changes to the map are
     * visible through the returned instance.
     *
     * @param map the map to wrap
     * @return a {@link ReadOnlySettings} view of the given map
     */
    static ReadOnlySettings from(Map<String, String> map) {
        return new ReadOnlySettingsImpl(map);
    }

    /**
     * Returns a {@link ReadOnlySettings} backed by the given properties. Subsequent changes to the
     * properties are visible through the returned instance. Assumes that all keys and values are
     * strings.
     *
     * @param properties the properties to wrap
     * @return a {@link ReadOnlySettings} view of the given properties
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static ReadOnlySettings from(Properties properties) {
        return from((Map) properties);
    }

    /**
     * Returns a {@link ReadOnlySettings} backed by the current process environment variables. The
     * {@code filter} predicate is applied on every key access: {@link #getProperty(String)},
     * {@link #containsKey(String)}, and iteration only expose entries whose key the predicate
     * accepts. If {@code filter} is {@code null}, all keys are accepted.
     *
     * @param filter optional key filter; {@code null} accepts all keys
     * @return a {@link ReadOnlySettings} view over the filtered environment variables
     */
    static ReadOnlySettings fromEnvironment(Predicate<String> filter) {
        return new ReadOnlyFilteringSettings(
            System.getenv(), filter, ReadOnlyFilteringSettings.ENVIRONMENT_KEY_TRANSFORMER);
    }

    /**
     * Returns the decoded value for the given key, or {@code null} if no such key exists.
     *
     * @param key the key to look up
     * @return the decoded value, or {@code null} if not present
     */
    String getProperty(String key);

    /**
     * Returns whether a value is registered for the given key.
     *
     * @param key the key to check
     * @return {@code true} if a value is registered for the key, otherwise {@code false}
     */
    boolean containsKey(String key);

    /**
     * Returns a sequential {@link Stream} over the entries of these settings. The stream uses the
     * {@link #iterator() iterator} of this {@link Iterable}.
     *
     * @return a stream of the current entries
     */
    default Stream<Entry<String, String>> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Returns the set of registered keys. The default implementation walks the
     * {@link #iterator() iterator}; implementations are expected to override this with a direct
     * lookup against the backing storage for efficiency. Filtering implementations must apply the
     * key filter so that only visible keys appear in the returned set.
     *
     * @return an unmodifiable set of the registered keys
     */
    default Set<String> keySet() {
        return stream().map(Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the value for the given key parsed as an {@code int}. If no value is registered the
     * given default is returned. If the registered value cannot be parsed as an integer, a warning
     * is logged and the default is returned.
     *
     * @param key          the key to look up
     * @param defaultValue the value to return when the key is missing or the value is not a valid integer
     * @return the parsed integer or {@code defaultValue} on missing/invalid input
     */
    default int getInt(String key, int defaultValue) {
        return parseInt(key, getProperty(key), defaultValue);
    }

    /**
     * Returns the value for the given key parsed as a {@code long}. If no value is registered the
     * given default is returned. If the registered value cannot be parsed as a long, a warning is
     * logged and the default is returned.
     *
     * @param key          the key to look up
     * @param defaultValue the value to return when the key is missing or the value is not a valid long
     * @return the parsed long or {@code defaultValue} on missing/invalid input
     */
    default long getLong(String key, long defaultValue) {
        return parseLong(key, getProperty(key), defaultValue);
    }

    /**
     * Returns the value for the given key parsed as a {@code boolean}. Only the case-insensitive
     * literals {@code "true"} and {@code "false"} are accepted. If no value is registered the given
     * default is returned. If the registered value is anything else, a warning is logged and the
     * default is returned.
     *
     * @param key          the key to look up
     * @param defaultValue the value to return when the key is missing or the value is not a valid boolean
     * @return the parsed boolean or {@code defaultValue} on missing/invalid input
     */
    default boolean getBool(String key, boolean defaultValue) {
        return parseBool(key, getProperty(key), defaultValue);
    }

    /**
     * Same as {@link #getInt(String, int)} with deprecated-key fallback. The deprecated key is
     * tried only if {@code expectedKey} is not present; a deprecation warning is logged when the
     * fallback is used. If neither key is present (or both yield unparseable values), the default
     * is returned.
     *
     * @param expectedKey   the expected (current) key
     * @param defaultValue  the value to return when neither key is present or both values are invalid
     * @param deprecatedKey deprecated key to try if the expected key does not exist
     * @return the parsed integer or {@code defaultValue}
     */
    default int getIntWithDeprecationWarning(String expectedKey, int defaultValue, String deprecatedKey) {
        return parseInt(expectedKey, getPropertyWithDeprecationWarning(expectedKey, deprecatedKey), defaultValue);
    }

    /**
     * Same as {@link #getLong(String, long)} with deprecated-key fallback. The deprecated key is
     * tried only if {@code expectedKey} is not present; a deprecation warning is logged when the
     * fallback is used.
     *
     * @param expectedKey   the expected (current) key
     * @param defaultValue  the value to return when neither key is present or both values are invalid
     * @param deprecatedKey deprecated key to try if the expected key does not exist
     * @return the parsed long or {@code defaultValue}
     */
    default long getLongWithDeprecationWarning(String expectedKey, long defaultValue, String deprecatedKey) {
        return parseLong(expectedKey, getPropertyWithDeprecationWarning(expectedKey, deprecatedKey), defaultValue);
    }

    /**
     * Same as {@link #getBool(String, boolean)} with deprecated-key fallback. The deprecated key is
     * tried only if {@code expectedKey} is not present; a deprecation warning is logged when the
     * fallback is used.
     *
     * @param expectedKey   the expected (current) key
     * @param defaultValue  the value to return when neither key is present or both values are invalid
     * @param deprecatedKey deprecated key to try if the expected key does not exist
     * @return the parsed boolean or {@code defaultValue}
     */
    default boolean getBoolWithDeprecationWarning(String expectedKey, boolean defaultValue, String deprecatedKey) {
        return parseBool(expectedKey, getPropertyWithDeprecationWarning(expectedKey, deprecatedKey), defaultValue);
    }

    private static int parseInt(String key, String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(ReadOnlySettings.class).warn(
                "Setting [{}] has invalid integer value [{}]; using default [{}]", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static long parseLong(String key, String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(ReadOnlySettings.class).warn(
                "Setting [{}] has invalid long value [{}]; using default [{}]", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean parseBool(String key, String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        LoggerFactory.getLogger(ReadOnlySettings.class).warn(
            "Setting [{}] has invalid boolean value [{}]; using default [{}]", key, value, defaultValue);
        return defaultValue;
    }

    /**
     * Returns the case-insensitive substring tokens used to mark property keys as secured. A
     * property key whose lowercased form contains any of these tokens is considered secured and its
     * value is masked in log output.
     *
     * @return an unmodifiable view of the registered secured-key tokens
     */
    Set<String> getSecuredProperties();

    /**
     * Prints all registered properties to the given logger. Values of secured keys (e.g. passwords,
     * credentials) are masked.
     *
     * @param logger the logger used for printing properties
     */
    default void printPropertiesWithoutPasswords(Logger logger) {
        Set<String> secured = getSecuredProperties();
        List<Entry<String, String>> entries = new ArrayList<>();
        forEach(entries::add);
        entries.sort(Entry.comparingByKey());
        for (Entry<String, String> entry : entries) {
            String lowerKey = entry.getKey().toLowerCase();
            if (secured.stream().anyMatch(lowerKey::contains)) {
                logger.info("***      {} = ****", entry.getKey());
            } else {
                logger.info("***      {} = {}", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Returns a copy of the properties whose keys start with the given prefix.
     *
     * @param prefix the prefix used to select properties
     * @return new {@link Properties} containing the matching entries
     */
    default Properties filter(String prefix) {
        Properties result = new Properties();
        for (Entry<String, String> entry : this) {
            if (entry.getKey().startsWith(prefix)) {
                result.setProperty(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Same as {@link #getPropertyWithDeprecationWarning(String, String, String)} with {@code null} as default value.
     *
     * @param expectedKey   the expected (current) key
     * @param deprecatedKey deprecated key to try if the expected key does not exist
     * @return see {@link #getPropertyWithDeprecationWarning(String, String, String)}
     */
    default String getPropertyWithDeprecationWarning(String expectedKey, String deprecatedKey) {
        return getPropertyWithDeprecationWarning(expectedKey, null, deprecatedKey);
    }

    /**
     * Returns the value for {@code expectedKey} if present. Otherwise, if {@code deprecatedKey} is present,
     * a deprecation warning is logged and its value is returned. If neither key is present, {@code defaultValue}
     * is returned.
     *
     * @param expectedKey   the expected (current) key
     * @param defaultValue  the value to return when neither key is present
     * @param deprecatedKey deprecated key to try if the expected key does not exist
     * @return the resolved value, or {@code defaultValue} if no key is present
     */
    default String getPropertyWithDeprecationWarning(String expectedKey, String defaultValue, String deprecatedKey) {
        if (containsKey(expectedKey)) {
            return getProperty(expectedKey);
        }
        if (containsKey(deprecatedKey)) {
            LoggerFactory.getLogger(ReadOnlySettings.class)
                .warn("Setting [{}] is outdated. Use [{}] instead.", deprecatedKey, expectedKey);
            return getProperty(deprecatedKey);
        }
        return defaultValue;
    }

    /**
     * Registers additional keys whose values must be treated as secret and masked in log output.
     * Keys are matched case-insensitively as substrings of the actual property keys.
     *
     * @param secureProperties the keys to register as secured
     */
    void addSecureProperties(Set<String> secureProperties);
}
