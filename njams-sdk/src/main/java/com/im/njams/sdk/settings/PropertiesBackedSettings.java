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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.im.njams.sdk.settings.encoding.Transformer;

/**
 * A {@link WritableSettings} backed by a live {@link Properties} instance. Every operation is
 * routed exclusively through the public Properties string API ({@link Properties#getProperty},
 * {@link Properties#setProperty}, {@link Properties#stringPropertyNames}) — the inherited
 * {@link java.util.Hashtable}-typed Map methods on Properties are never touched. This keeps the
 * wrapper consistent with Properties subclasses that override the string API but leave their
 * inherited Map methods stale relative to their own storage (e.g. Camel's
 * {@code OrderedLocationProperties}).
 */
class PropertiesBackedSettings extends AbstractReadOnlySettings implements WritableSettings {

    private final Properties properties;

    PropertiesBackedSettings(Properties properties) {
        this.properties = properties;
    }

    @Override
    public String getProperty(String key) {
        return Transformer.decode(properties.getProperty(key));
    }

    @Override
    public boolean containsKey(String key) {
        // Properties cannot hold a null value: setProperty(k, null) throws NPE.
        // So a null result from getProperty unambiguously means "not present".
        return properties.getProperty(key) != null;
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(properties.stringPropertyNames());
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return properties.stringPropertyNames().stream()
            .map(name -> Map.entry(name, Transformer.decode(properties.getProperty(name))))
            .iterator();
    }

    @Override
    public void put(String key, String value) {
        properties.setProperty(key, value);
    }
}
