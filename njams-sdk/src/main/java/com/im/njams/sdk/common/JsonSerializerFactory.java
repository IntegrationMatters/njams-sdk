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
package com.im.njams.sdk.common;

import com.faizsiegeln.njams.messageformat.v4.converter.Converter;
import com.faizsiegeln.njams.messageformat.v4.converter.DefaultConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>THIS IS FOR INTERNAL USE ONLY!</b>
 * For simple serialization and parsing tasks prefer {@link com.im.njams.sdk.utils.JsonUtils};
 * for registering custom (de-)serializers use
 * {@link #addSerializer(com.faizsiegeln.njams.messageformat.v4.converter.Converter, boolean)}.
 *
 * <p>Provides factory methods for JSON serializers and mappers. All mapper instances are cached for re-using.
 * Thus, the 'fast-mapper' label is somewhat misleading now. It ony means 'a non-pretty-printing' mapper.
 *
 * <p>The methods that accept or return Jackson types ({@code ObjectMapper}, {@code ObjectWriter}) are only
 * available through {@link #_internal()}. Jackson is bundled as an internal dependency and relocated to a private
 * package namespace during SDK packaging, making those types unreachable by SDK consumers in the packaged artifact.
 *
 * @author cwinkler
 *
 */
public class JsonSerializerFactory {
    private static class Mapper<T> {
        private final JsonSerializer<T> serializer;
        private final JsonDeserializer<T> deserializer;

        private Mapper(JsonSerializer<T> serializer, JsonDeserializer<T> deserializer) {
            this.serializer = Objects.requireNonNull(serializer);
            this.deserializer = Objects.requireNonNull(deserializer);
        }

        private Class<T> getType() {
            return serializer.handledType();
        }

        @Override
        public String toString() {
            return "Mapper[" + getType().getName() + "]";
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(JsonSerializerFactory.class);

    /**
     * ID for the filter used by the mix-in interface.
     */
    protected static final String MIX_IN_FILTER_ID = "MixInFilter";

    private static final Map<Class<?>, Mapper<?>> customSerializers = new ConcurrentHashMap<>();

    private static final Map<Byte, ObjectMapper> mapperCache = new ConcurrentHashMap<>(4, 1f);

    private static ObjectMapper getCachedMapper(boolean pretty, boolean skipNull) {
        // key is a flag bitmap fpr the four possible combinations of the given booleans
        final byte key = (byte) ((pretty ? 0b10 : 0) | (skipNull ? 0b01 : 0));
        return mapperCache.computeIfAbsent(key, k -> createMapper(skipNull, pretty));
    }

    private JsonSerializerFactory() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Jackson databind: {}", ObjectMapper.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson core: {}",
                JsonFactory.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson module.jaxb: {}",
                JaxbAnnotationIntrospector.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson module.jakarta: {}",
                JakartaXmlBindAnnotationIntrospector.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson annotation: {}",
                JsonInclude.Include.class.getProtectionDomain().getCodeSource().getLocation());
        }
        addMessageFormatConverters();
    }

    @SuppressWarnings("unchecked")
    private static synchronized ObjectMapper createDefaultMapper() {
        ObjectMapper om = new ObjectMapper();

        AnnotationIntrospector first = new JacksonAnnotationIntrospector();
        AnnotationIntrospector second = new JaxbAnnotationIntrospector(om.getTypeFactory());
        AnnotationIntrospector third = new JakartaXmlBindAnnotationIntrospector(om.getTypeFactory());
        AnnotationIntrospector triple = AnnotationIntrospector.pair(AnnotationIntrospector.pair(first, second), third);
        om.setAnnotationIntrospector(triple);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        // ensure that default converters are registered
        addMessageFormatConverters();
        final SimpleModule customSerializersModule = new SimpleModule();
        for (@SuppressWarnings("rawtypes") final Mapper mapper : customSerializers.values()) {
            LOG.trace("Adding {}", mapper);
            customSerializersModule.addSerializer(mapper.getType(), mapper.serializer);
            customSerializersModule.addDeserializer(mapper.getType(), mapper.deserializer);
        }
        om.registerModule(customSerializersModule);
        return om;
    }

    private static <T> void addMessageFormatConverters() {
        DefaultConverter.getAll().forEach(c -> addSerializer(c, false));
    }

    /**
     * Registers a pair of serializer/deserialzer derived from the given message-format {@link Converter} instance.
     * @param <T> Object type for which the serializers should be added
     * @param converter The {@link Converter} implementation used for serializing and deserializing.
     * @param replace If <code>true</code> any registered serializer for the same type is replaced. Otherwise, if a
     * serializer for the same type is already registered, this method does nothing. Be careful with overwriting default
     * serializers!
     */
    public static <T> void addSerializer(Converter<T> converter, boolean replace) {
        final Entry<StdSerializer<T>, StdDeserializer<T>> instance = buildSerializer(converter);
        addSerializer(instance.getKey(), instance.getValue(), replace);
    }

    @SuppressWarnings("serial")
    private static <T> Entry<StdSerializer<T>, StdDeserializer<T>> buildSerializer(Converter<T> converter) {
        return new AbstractMap.SimpleImmutableEntry<StdSerializer<T>, StdDeserializer<T>>(
            new StdSerializer<T>(converter.getType()) {

                @Override
                public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                    try {
                        final String json = converter.serialize(value);
                        if (json == null) {
                            gen.writeNull();
                        } else {
                            gen.writeString(json);
                        }
                    } catch (Exception e) {
                        new IOException("Failed to serialize: " + value, e);
                    }
                }
            }, new StdDeserializer<T>(converter.getType()) {

            @Override
            public T deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException, JacksonException {
                try {
                    final JsonNode node = jp.getCodec().readTree(jp);
                    if (node == null || !node.isTextual()) {
                        return null;
                    }
                    return converter.deserialize(node.asText());
                } catch (Exception e) {
                    throw new IOException("Failed to deserialize", e);
                }
            }
        });
    }

    private static synchronized <T> void addSerializer(JsonSerializer<T> serializer, JsonDeserializer<T> deserializer,
        boolean replace) {
        final Class<T> type = serializer.handledType();
        if (!replace && customSerializers.containsKey(type)) {
            LOG.debug("Skip adding new serializer because there is already one registered for type {}", type.getName());
            return;
        }
        LOG.trace("Register new mapper for {}", type.getName());
        mapperCache.clear();
        customSerializers.put(type, new Mapper<>(serializer, deserializer));
    }

    /**
     * Returns whether or not a custom serializer is currently registered for the given type.
     * Note that default converters are lazily added when needed, i.e., for such types, this method may wrongly return
     * <code>false</code> because the according serializer is not yet created, but it will, when needed.
     *
     * @param type The type to check.
     * @return <code>true</code> if a serializer is registered for the given type.
     */
    public static boolean hasSerializer(Class<?> type) {
        return customSerializers.containsKey(type);
    }

    /**
     * Removes any custom serializer mapping for the given type.
     * Required default serializers are automatically re-added, i.e., for such types, this method behaves like
     * reset-to-default.
     *
     * @param type The type for that the custom serializer shall be removed.
     * @return <code>true</code> only if there was a serializer registered for the given type.
     */
    public static synchronized boolean removeSerializer(Class<?> type) {
        if (customSerializers.remove(type) != null) {
            mapperCache.clear();
            return true;
        }
        return false;
    }

    private static ObjectMapper createMapper(boolean skipNullValues, boolean pretty) {
        ObjectMapper om = createDefaultMapper();
        om.setSerializationInclusion(skipNullValues ? Include.NON_NULL : Include.ALWAYS);
        om.configure(SerializationFeature.INDENT_OUTPUT, pretty);
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, pretty);
        return om;
    }

    /**
     * Returns the accessor for the Jackson-typed factory methods.
     * <p>
     * <b>SDK-internal. Client code must not use this.</b> The returned methods expose Jackson types, which are
     * relocated during SDK packaging and therefore unreachable for SDK consumers. Use
     * {@link com.im.njams.sdk.utils.JsonUtils} for serialization and parsing instead.
     *
     * @return The (constant) accessor instance.
     */
    public static Internal _internal() {
        return Internal.INSTANCE;
    }

    /**
     * Holder for the factory methods that expose Jackson types.
     * <p>
     * <b>SDK-internal. Client code must not use this.</b> Jackson types are relocated during SDK packaging and
     * therefore unreachable for SDK consumers. Use {@link com.im.njams.sdk.utils.JsonUtils} instead.
     */
    public static final class Internal {
        private static final Internal INSTANCE = new Internal();

        private Internal() {
            // singleton
        }

        /**
         * Returns a cached default mapper with configuration that is required for all created serializers.
         * This instance is cached and optimized for performance instead of readability, e.g., it does not apply
         * pretty-printing as the mapper provided by {@link #getDefaultMapper()}.
         *
         * @return the ObjectMapper.
         */
        public ObjectMapper getFastMapper() {
            return getCachedMapper(false, true);
        }

        /**
         * Returns a cached default mapper with configuration that is required for all created
         * serializers. skipNullValues and pretty will be set to true.
         *
         * @return the ObjectMapper.
         */
        public ObjectMapper getDefaultMapper() {
            return getCachedMapper(true, true);
        }

        /**
         * Returns a cached mapper with some special settings.
         *
         * @param skipNullValues if true all null values will not be serialized.
         * @param pretty if true the result JSON will be prettyfied.
         * @return the ObjectMapper with the selected settings.
         */
        public ObjectMapper getMapper(boolean skipNullValues, boolean pretty) {
            return getCachedMapper(pretty, skipNullValues);
        }

        /**
         * Returns a Json writer configured according to the given settings.
         *
         * @param skipNullValues If set to <code>true</code>, properties that have a
         * <code>null</code> value are not serialized.
         * @param pretty If set to to <code>true</code>, the writer will format the
         * Json output.
         * @return the ObjectWriter
         */
        public ObjectWriter createWriter(boolean skipNullValues, boolean pretty) {
            return getMapper(skipNullValues, pretty).writer();
        }
    }
}
