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
 * Read/write view of the nJAMS SDK settings. Extends {@link ReadOnlyClientSettings} with operations that
 * modify the registered properties. This is the interface most SDK consumers work with — see
 * {@link ReadOnlyClientSettings} for context on when the read-only restriction is meaningful.
 */
public interface ClientSettings extends ReadOnlyClientSettings {

    /**
     * Returns a {@link ClientSettings} backed by the given map. Subsequent changes to the map are
     * visible through the returned instance, and writes through the instance are reflected in the
     * map.
     *
     * @param map the map to wrap
     * @return a {@link ClientSettings} view of the given map
     */
    static ClientSettings from(Map<String, String> map) {
        return new WritableSettingsImpl(map);
    }

    /**
     * Returns a {@link ClientSettings} backed by the given properties. Subsequent changes to the
     * properties are visible through the returned instance, and writes through the instance are
     * reflected in the properties. Reads and writes go exclusively through the public Properties
     * string API ({@link Properties#getProperty}, {@link Properties#setProperty},
     * {@link Properties#stringPropertyNames}), which keeps the wrapper consistent with
     * {@code Properties} subclasses that override the string API while leaving inherited
     * {@link java.util.Hashtable}-typed methods stale (e.g. Camel's
     * {@code OrderedLocationProperties}).
     *
     * @param properties the properties to wrap
     * @return a {@link ClientSettings} view of the given properties
     */
    static ClientSettings from(Properties properties) {
        return new PropertiesBackedSettings(properties);
    }

    /**
     * Returns a {@link ClientSettings} backed by the current JVM system properties. The
     * {@code filter} predicate is applied on every read access ({@link #getProperty(String)},
     * {@link #containsKey(String)}, iteration, and methods derived from them): a setting is only
     * visible if the predicate accepts its key. Writes via {@link #put(String, String)} or
     * {@link #putAll(Map)} are unfiltered and propagate to {@link System#getProperties()}. If
     * {@code filter} is {@code null}, all keys are accepted.
     * <p>
     * Reads and writes go through the public {@link Properties} string API (via
     * {@link #from(Properties)}), so the wrapper stays consistent even if
     * {@link System#getProperties()} has been replaced with a {@link Properties} subclass that
     * overrides only a subset of the inherited {@link java.util.Hashtable}-typed methods.
     *
     * @param filter optional key filter; {@code null} accepts all keys
     * @return a {@link ClientSettings} view over the (read-filtered) system properties
     */
    static ClientSettings fromSystemProperties(Predicate<String> filter) {
        return new FilteringWritableSettings(from(System.getProperties()), filter);
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
