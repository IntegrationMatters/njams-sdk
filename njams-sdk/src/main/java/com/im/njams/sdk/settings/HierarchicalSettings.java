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
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.StringUtils;

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

    private static final Logger LOG = LoggerFactory.getLogger(HierarchicalSettings.class);

    private static final int MAX_NAME_WIDTH = 20;

    private static final String WRITABILITY_PROBE_KEY = "__njams_hsettings_writable_probe__";

    private final List<NamedLayer> layers;

    private HierarchicalSettings(List<NamedLayer> layers) {
        this.layers = List.copyOf(layers);
        LOG.info("Settings lookup chain: {}", this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layers.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append('[').append(layers.get(i).name).append(']');
        }
        return sb.toString();
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
     * Convenience factory that wraps the given map in {@link WritableSettings#from(Map)} and
     * uses it as the base layer.
     * <p>
     * <strong>The map must accept write operations.</strong> Writes through the hierarchical
     * settings are dispatched to the base layer, so an immutable or unmodifiable map would fail
     * the first time a property is set. To catch this early, the map is probed with a
     * {@code put}/{@code remove} of a reserved sentinel key at this call; if the probe throws
     * <em>any</em> exception (the JDK's {@link UnsupportedOperationException}, an implementation-
     * specific {@code IllegalStateException}, a custom security check, etc.), an
     * {@link IllegalArgumentException} is thrown with the original cause attached.
     *
     * @param base the writable map to use as the base layer; must not be {@code null}
     * @return a builder for adding further layers
     * @throws NullPointerException     if {@code base} is {@code null}
     * @throws IllegalArgumentException if the writability probe on {@code base} throws any exception
     */
    public static Builder from(Map<String, String> base) {
        Objects.requireNonNull(base, "base map must not be null");
        requireWritable(base);
        return from(WritableSettings.from(base));
    }

    /**
     * Convenience factory that wraps the given properties in
     * {@link WritableSettings#from(Properties)} and uses them as the base layer.
     * <p>
     * <strong>The properties must accept write operations.</strong> Writes through the
     * hierarchical settings are dispatched to the base layer, so a {@link Properties} subclass
     * that rejects mutations would fail the first time a property is set. To catch this early,
     * the properties are probed with a {@code setProperty}/{@code remove} of a reserved sentinel
     * key at this call; if the probe throws <em>any</em> exception (the JDK's
     * {@link UnsupportedOperationException}, an implementation-specific
     * {@code IllegalStateException}, a custom security check, etc.), an
     * {@link IllegalArgumentException} is thrown with the original cause attached.
     *
     * @param base the writable properties to use as the base layer; must not be {@code null}
     * @return a builder for adding further layers
     * @throws NullPointerException     if {@code base} is {@code null}
     * @throws IllegalArgumentException if the writability probe on {@code base} throws any exception
     */
    public static Builder from(Properties base) {
        Objects.requireNonNull(base, "base properties must not be null");
        requireWritable(base);
        return from(WritableSettings.from(base));
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

    private static void requireWritable(Map<String, String> map) {
        try {
            map.put(WRITABILITY_PROBE_KEY, "");
            map.remove(WRITABILITY_PROBE_KEY);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Base map for HierarchicalSettings.from(Map) must accept writes; "
                    + "probe put/remove threw " + e.getClass().getSimpleName(), e);
        }
    }

    private static void requireWritable(Properties properties) {
        try {
            properties.setProperty(WRITABILITY_PROBE_KEY, "");
            properties.remove(WRITABILITY_PROBE_KEY);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Base properties for HierarchicalSettings.from(Properties) must accept writes; "
                    + "probe setProperty/remove threw " + e.getClass().getSimpleName(), e);
        }
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
        int nameWidth = 0;
        for (NamedLayer layer : source.values()) {
            int len = Math.min(layer.name.length(), MAX_NAME_WIDTH);
            if (len > nameWidth) {
                nameWidth = len;
            }
        }
        String namePattern = "%-" + Math.max(nameWidth, 1) + "s";
        List<String> sortedKeys = new ArrayList<>(source.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            NamedLayer layer = source.get(key);
            String paddedName = String.format(namePattern, displayName(layer.name));
            if (isSecured(key, layer.settings.getSecuredProperties())) {
                logger.info("***      [{}]  {} = ****", paddedName, key);
            } else {
                logger.info("***      [{}]  {} = {}", paddedName, key, values.get(key));
            }
        }
    }

    private static String displayName(String name) {
        return name.length() <= MAX_NAME_WIDTH ? name : StringUtils.abbreviate(name, MAX_NAME_WIDTH - 1);
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
        final ReadOnlyClientSetting settings;

        NamedLayer(String name, ReadOnlyClientSetting settings) {
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
        final ReadOnlyClientSetting settings;

        CommonPending(ReadOnlyClientSetting settings) {
            this.settings = settings;
        }

        @Override
        NamedLayer materialize() {
            return new NamedLayer(name != null ? name : defaultName(settings), settings);
        }

        private static String defaultName(ReadOnlyClientSetting settings) {
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
            ReadOnlyClientSetting settings = kind == LayerKind.ENVIRONMENT
                ? ReadOnlyClientSetting.fromEnvironment(filter)
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
         * layers this overrides the default name. If the previous {@link #andThen(ReadOnlyClientSetting)}
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
        public Builder andThen(ReadOnlyClientSetting settings) {
            commit();
            if (settings != null) {
                pending = new CommonPending(settings);
            }
            return this;
        }

        /**
         * Convenience overload that wraps the given map via {@link ReadOnlyClientSetting#from(Map)} and
         * appends it as the next layer. {@code null} is accepted and silently skipped. The map
         * does not need to be writable — non-base layers are consulted read-only by the
         * hierarchical view.
         *
         * @param map the next layer's backing map, may be {@code null}
         * @return this builder
         */
        public Builder andThen(Map<String, String> map) {
            return andThen(map == null ? null : ReadOnlyClientSetting.from(map));
        }

        /**
         * Convenience overload that wraps the given properties via
         * {@link WritableSettings#from(Properties)} and appends them as the next layer.
         * {@code null} is accepted and silently skipped. The properties do not need to be
         * writable — non-base layers are consulted read-only by the hierarchical view. Routing
         * through {@link WritableSettings#from(Properties)} keeps the wrapper safe with
         * {@link Properties} subclasses that override only a subset of the inherited
         * {@link java.util.Hashtable}-typed methods.
         *
         * @param properties the next layer's backing properties, may be {@code null}
         * @return this builder
         */
        public Builder andThen(Properties properties) {
            return andThen(properties == null ? null : WritableSettings.from(properties));
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
         * See {@link Builder#andThen(ReadOnlyClientSetting)}.
         *
         * @param settings the next layer, may be {@code null}
         * @return the common builder
         */
        public Builder andThen(ReadOnlyClientSetting settings) {
            return builder.andThen(settings);
        }

        /**
         * See {@link Builder#andThen(Map)}.
         *
         * @param map the next layer's backing map, may be {@code null}
         * @return the common builder
         */
        public Builder andThen(Map<String, String> map) {
            return builder.andThen(map);
        }

        /**
         * See {@link Builder#andThen(Properties)}.
         *
         * @param properties the next layer's backing properties, may be {@code null}
         * @return the common builder
         */
        public Builder andThen(Properties properties) {
            return builder.andThen(properties);
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
