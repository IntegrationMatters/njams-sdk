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

import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * Read/write view of the nJAMS SDK settings. Extends {@link ReadOnlySettings} with operations that
 * modify the registered properties.
 */
public interface WritableSettings extends ReadOnlySettings {

    /**
     * Returns a {@link WritableSettings} backed by the given map. Subsequent changes to the map are
     * visible through the returned instance, and writes through the instance are reflected in the
     * map.
     *
     * @param map the map to wrap
     * @return a {@link WritableSettings} view of the given map
     */
    static WritableSettings from(Map<String, String> map) {
        return new WritableSettingsImpl(map);
    }

    /**
     * Returns a {@link WritableSettings} backed by the given properties. Subsequent changes to the
     * properties are visible through the returned instance, and writes through the instance are
     * reflected in the properties. Assumes that all keys and values are strings.
     *
     * @param properties the properties to wrap
     * @return a {@link WritableSettings} view of the given properties
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static WritableSettings from(Properties properties) {
        return from((Map) properties);
    }

    /**
     * Returns a {@link WritableSettings} backed by the current JVM system properties. The
     * {@code filter} predicate is applied on every read access ({@link #getProperty(String)},
     * {@link #containsKey(String)}, iteration, and methods derived from them): a setting is only
     * visible if the predicate accepts its key. Writes via {@link #put(String, String)} or
     * {@link #putAll(Map)} are unfiltered and propagate to {@link System#getProperties()}. If
     * {@code filter} is {@code null}, all keys are accepted.
     *
     * @param filter optional key filter; {@code null} accepts all keys
     * @return a {@link WritableSettings} view over the (read-filtered) system properties
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static WritableSettings fromSystemProperties(Predicate<String> filter) {
        return new FilteringWritableSettings((Map) System.getProperties(), filter);
    }

    /**
     * Stores the given value under the given key, replacing any previous value.
     *
     * @param key   the key
     * @param value the value
     */
    void put(String key, String value);

    /**
     * Stores all entries from the given map, replacing any previous values for the same keys.
     *
     * @param entries the entries to add
     */
    default void putAll(Map<String, String> entries) {
        entries.forEach(this::put);
    }
}
