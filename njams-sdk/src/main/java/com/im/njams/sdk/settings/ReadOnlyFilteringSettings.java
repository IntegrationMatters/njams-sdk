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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ReadOnlyFilteringSettings extends AbstractReadOnlySettings {

    static final Function<String, String> ENVIRONMENT_KEY_TRANSFORMER = key -> {
        if (key == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    };

    final ReadOnlySettings inner;
    private final Predicate<String> filter;
    private final Function<String, String> keyTransformer;
    private final Map<String, String> keyCache;

    ReadOnlyFilteringSettings(ReadOnlySettings inner, Predicate<String> filter) {
        this(inner, filter, null);
    }

    ReadOnlyFilteringSettings(ReadOnlySettings inner, Predicate<String> filter,
        Function<String, String> keyTransformer) {
        this.inner = inner;
        this.filter = filter == null ? key -> true : filter;
        this.keyTransformer = keyTransformer;
        this.keyCache = keyTransformer == null ? null : new ConcurrentHashMap<>();
    }

    String transformKey(String key) {
        return keyTransformer == null ? key : keyCache.computeIfAbsent(key, keyTransformer);
    }

    @Override
    public String getProperty(String key) {
        String transformed = transformKey(key);
        return filter.test(transformed) ? inner.getProperty(transformed) : null;
    }

    @Override
    public boolean containsKey(String key) {
        String transformed = transformKey(key);
        return filter.test(transformed) && inner.containsKey(transformed);
    }

    @Override
    public Set<String> keySet() {
        return inner.keySet().stream()
            .filter(filter)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return inner.stream()
            .filter(e -> filter.test(e.getKey()))
            .iterator();
    }
}
