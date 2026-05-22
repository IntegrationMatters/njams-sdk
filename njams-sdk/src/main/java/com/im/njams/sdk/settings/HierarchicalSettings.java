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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;

/**
 * A {@link WritableSettings} that composes an ordered list of named layers. Each read operation
 * consults the layers in order and returns the result from the first layer that contains the
 * requested key. Write operations ({@link #put(String, String)}, {@link #putAll(Map)},
 * {@link #addSecureProperties(Set)}) are applied exclusively to the base layer supplied to
 * {@link #from(WritableSettings)}.
 * <p>
 * Construction goes through a fluent builder:
 * <pre>{@code
 * WritableSettings settings = HierarchicalSettings.from(myDefaults).withName("defaults")
 *     .andThen(fileSettings).withName(fileName)
 *     .andThenSystemProperties().withPrefixFilter("njams.")
 *     .andThenEnvironmentVariables().withPrefixFilter("NJAMS_")
 *     .build();
 * }</pre>
 * Layer names appear in {@link #printPropertiesWithoutPasswords(Logger)} output to attribute each
 * entry to its source. Each layer carries its own secured-keys configuration; a value is masked
 * based on the source layer's tokens, not on a global union.
 */
public final class HierarchicalSettings implements WritableSettings {

    private final List<NamedLayer> layers;

    private HierarchicalSettings(List<NamedLayer> layers) {
        this.layers = List.copyOf(layers);
    }

    /**
     * Starts a new hierarchical settings chain. The given {@code base} is the only layer that
     * receives writes; subsequent {@code andThen(...)} layers are read-only with respect to the
     * hierarchical view.
     *
     * @param base the base layer; must not be {@code null}
     * @return a builder for adding further layers
     * @throws NullPointerException if {@code base} is {@code null}
     */
    public static Builder from(WritableSettings base) {
        Objects.requireNonNull(base, "base settings must not be null");
        return new Builder(base);
    }

    /**
     * Starts a new hierarchical settings chain with an in-memory, initially empty base layer.
     * The base is a {@link WritableSettings} backed by a fresh {@link LinkedHashMap}; writes
     * through the resulting hierarchical settings are stored there and discarded when the
     * instance becomes unreachable. The base layer is named {@code <in-memory>} by default;
     * override with {@link Builder#withName(String)} if needed.
     *
     * @return a builder for adding further layers
     */
    public static Builder fromEmpty() {
        return new Builder(WritableSettings.from(new LinkedHashMap<>())).withName("<in-memory>");
    }

    private WritableSettings base() {
        return (WritableSettings) layers.get(0).settings;
    }

    @Override
    public String getProperty(String key) {
        for (NamedLayer layer : layers) {
            if (layer.settings.containsKey(key)) {
                return layer.settings.getProperty(key);
            }
        }
        return null;
    }

    @Override
    public boolean containsKey(String key) {
        for (NamedLayer layer : layers) {
            if (layer.settings.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void put(String key, String value) {
        base().put(key, value);
    }

    @Override
    public Set<String> getSecuredProperties() {
        return base().getSecuredProperties();
    }

    @Override
    public void addSecureProperties(Set<String> secureProperties) {
        base().addSecureProperties(secureProperties);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        Map<String, Entry<String, String>> firstByKey = new LinkedHashMap<>();
        for (NamedLayer layer : layers) {
            for (Entry<String, String> entry : layer.settings) {
                firstByKey.putIfAbsent(entry.getKey(), entry);
            }
        }
        return firstByKey.values().iterator();
    }

    @Override
    public void printPropertiesWithoutPasswords(Logger logger) {
        Map<String, NamedLayer> source = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        for (NamedLayer layer : layers) {
            for (Entry<String, String> entry : layer.settings) {
                if (!source.containsKey(entry.getKey())) {
                    source.put(entry.getKey(), layer);
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        }
        List<String> sortedKeys = new ArrayList<>(source.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            NamedLayer layer = source.get(key);
            if (isSecured(key, layer.settings.getSecuredProperties())) {
                logger.info("***      {} = **** [{}]", key, layer.name);
            } else {
                logger.info("***      {} = {} [{}]", key, values.get(key), layer.name);
            }
        }
    }

    private static boolean isSecured(String key, Set<String> tokens) {
        if (key == null || key.isEmpty() || tokens.isEmpty()) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static final class NamedLayer {
        final String name;
        final ReadOnlySettings settings;

        NamedLayer(String name, ReadOnlySettings settings) {
            this.name = name;
            this.settings = settings;
        }
    }

    private enum LayerKind {
        SYSTEM_PROPERTIES("<system properties>"),
        ENVIRONMENT("<environment variables>");

        final String defaultName;

        LayerKind(String defaultName) {
            this.defaultName = defaultName;
        }
    }

    private abstract static class Pending {
        String name;
        abstract NamedLayer materialize();
    }

    private static final class CommonPending extends Pending {
        final ReadOnlySettings settings;

        CommonPending(ReadOnlySettings settings) {
            this.settings = settings;
        }

        @Override
        NamedLayer materialize() {
            return new NamedLayer(name != null ? name : defaultName(settings), settings);
        }

        private static String defaultName(ReadOnlySettings settings) {
            return "<" + settings.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(settings)) + ">";
        }
    }

    private static final class FilterablePending extends Pending {
        final LayerKind kind;
        String prefix;
        String regex;

        FilterablePending(LayerKind kind) {
            this.kind = kind;
        }

        @Override
        NamedLayer materialize() {
            Predicate<String> filter = makeFilter();
            ReadOnlySettings settings = kind == LayerKind.ENVIRONMENT
                ? ReadOnlySettings.fromEnvironment(filter)
                : WritableSettings.fromSystemProperties(filter);
            return new NamedLayer(name != null ? name : kind.defaultName, settings);
        }

        private Predicate<String> makeFilter() {
            if (prefix == null && regex == null) {
                return key -> true;
            }
            Pattern pattern = regex == null ? null : Pattern.compile(regex);
            if (prefix != null && pattern != null) {
                return key -> key.startsWith(prefix) && pattern.matcher(key).matches();
            }
            if (prefix != null) {
                return key -> key.startsWith(prefix);
            }
            return key -> pattern.matcher(key).matches();
        }
    }

    /**
     * Fluent builder for a {@link HierarchicalSettings} instance. Returned by
     * {@link HierarchicalSettings#from(WritableSettings)} and from the common {@code andThen(...)}
     * step. After adding a system-properties or environment-variables layer, the chain narrows to
     * {@link FilterableLayerBuilder} which adds filter configuration methods.
     */
    public static final class Builder {

        private final List<NamedLayer> committed = new ArrayList<>();
        private Pending pending;

        private Builder(WritableSettings base) {
            pending = new CommonPending(base);
        }

        /**
         * Sets the name of the most recently added layer. For system-properties and environment
         * layers this overrides the default name. If the previous {@link #andThen(ReadOnlySettings)}
         * call received {@code null}, this is a no-op.
         *
         * @param name layer name
         * @return this builder
         */
        public Builder withName(String name) {
            if (pending != null) {
                pending.name = name;
            }
            return this;
        }

        /**
         * Appends the given settings as the next layer. {@code null} is accepted and silently
         * skipped.
         *
         * @param settings the next layer, may be {@code null}
         * @return this builder
         */
        public Builder andThen(ReadOnlySettings settings) {
            commit();
            if (settings != null) {
                pending = new CommonPending(settings);
            }
            return this;
        }

        /**
         * Appends a system-properties layer backed by {@link System#getProperties()}. The returned
         * builder additionally exposes prefix and regex filter configuration.
         *
         * @return a filter-capable builder for the new layer
         */
        public FilterableLayerBuilder andThenSystemProperties() {
            commit();
            pending = new FilterablePending(LayerKind.SYSTEM_PROPERTIES);
            return new FilterableLayerBuilder(this);
        }

        /**
         * Appends an environment-variables layer backed by {@link System#getenv()}. The returned
         * builder additionally exposes prefix and regex filter configuration.
         *
         * @return a filter-capable builder for the new layer
         */
        public FilterableLayerBuilder andThenEnvironmentVariables() {
            commit();
            pending = new FilterablePending(LayerKind.ENVIRONMENT);
            return new FilterableLayerBuilder(this);
        }

        /**
         * Finalises the builder and returns the configured hierarchical settings.
         *
         * @return a {@link WritableSettings} delegating reads across the configured layers and
         *     writes to the base layer
         */
        public WritableSettings build() {
            commit();
            return new HierarchicalSettings(committed);
        }

        private void commit() {
            if (pending != null) {
                NamedLayer layer = pending.materialize();
                if (layer != null) {
                    committed.add(layer);
                }
                pending = null;
            }
        }
    }

    /**
     * Extension of the builder chain returned after {@link Builder#andThenSystemProperties()} or
     * {@link Builder#andThenEnvironmentVariables()}. Adds prefix and regex filter configuration
     * for the most recently added system/environment layer; otherwise mirrors {@link Builder}.
     * Filters configured via {@link #withPrefixFilter(String)} and {@link #withRegexFilter(String)}
     * combine with AND semantics — a key passes only if it satisfies both. Filters are applied to
     * the storage key (the actual system-property name or environment-variable name).
     */
    public static final class FilterableLayerBuilder {

        private final Builder builder;

        private FilterableLayerBuilder(Builder builder) {
            this.builder = builder;
        }

        /**
         * Restricts the current system/environment layer to keys starting with the given prefix.
         *
         * @param prefix required key prefix
         * @return this builder
         */
        public FilterableLayerBuilder withPrefixFilter(String prefix) {
            ((FilterablePending) builder.pending).prefix = prefix;
            return this;
        }

        /**
         * Restricts the current system/environment layer to keys matching the given regular
         * expression in full (uses {@link java.util.regex.Matcher#matches()} semantics).
         *
         * @param regex required key pattern
         * @return this builder
         */
        public FilterableLayerBuilder withRegexFilter(String regex) {
            ((FilterablePending) builder.pending).regex = regex;
            return this;
        }

        /**
         * Overrides the default name for the current system/environment layer.
         *
         * @param name layer name
         * @return this builder
         */
        public FilterableLayerBuilder withName(String name) {
            builder.withName(name);
            return this;
        }

        /**
         * See {@link Builder#andThen(ReadOnlySettings)}.
         *
         * @param settings the next layer, may be {@code null}
         * @return the common builder
         */
        public Builder andThen(ReadOnlySettings settings) {
            return builder.andThen(settings);
        }

        /**
         * See {@link Builder#andThenSystemProperties()}.
         *
         * @return a filter-capable builder for the new layer
         */
        public FilterableLayerBuilder andThenSystemProperties() {
            return builder.andThenSystemProperties();
        }

        /**
         * See {@link Builder#andThenEnvironmentVariables()}.
         *
         * @return a filter-capable builder for the new layer
         */
        public FilterableLayerBuilder andThenEnvironmentVariables() {
            return builder.andThenEnvironmentVariables();
        }

        /**
         * See {@link Builder#build()}.
         *
         * @return the configured {@link WritableSettings}
         */
        public WritableSettings build() {
            return builder.build();
        }
    }
}
