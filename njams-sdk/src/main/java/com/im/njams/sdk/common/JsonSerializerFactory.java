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
package com.im.njams.sdk.common;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.converter.Converter;
import com.faizsiegeln.njams.messageformat.v4.converter.DefaultConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

/**
 * Provides factory methods for JSON serializers and mappers.
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
    private static ObjectMapper defaultMapper = null;
    private static ObjectMapper cachedMapper = null;

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

    /**
     * Returns a cached default mapper with configuration that is required for all created serializers.
     * This instance is cached and optimized for performance instead of readability, e.g., it does not apply
     * pretty-printing as the mapper provided by {@link #getDefaultMapper()}. 
     *
     * @return the ObjectMapper.
     */
    public static ObjectMapper getFastMapper() {
        final ObjectMapper om = cachedMapper;
        if (om != null) {
            return om;
        }
        synchronized (JsonSerializerFactory.class) {
            if (cachedMapper == null) {
                LOG.debug("Creating new fast mapper.");
                cachedMapper = getMapper(true, false);
            }
            return cachedMapper;
        }

    }

    /**
     * Returns a cached default mapper with configuration that is required for all created
     * serializers. skipNullValues and pretty will be set to true.
     *
     * @return the ObjectMapper.
     */
    public static ObjectMapper getDefaultMapper() {
        final ObjectMapper om = defaultMapper;
        if (om != null) {
            return om;
        }
        synchronized (JsonSerializerFactory.class) {
            if (defaultMapper == null) {
                LOG.debug("Creating new default mapper.");
                defaultMapper = getMapper(true, true);
            }
            return defaultMapper;
        }
    }

    /**
     * Allows to create a mapper with a different {@link JsonFactory} that uses
     * the same settings as the default JSON mappers used by this application.
     *
     * @param factory May be <code>null</code> to use the default JSON factors.
     * @return the default ObjectMapper
     */
    @SuppressWarnings("unchecked")
    public static synchronized ObjectMapper getDefaultMapper(JsonFactory factory) {
        ObjectMapper om = factory == null ? new ObjectMapper() : new ObjectMapper(factory);

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
        for (@SuppressWarnings("rawtypes")
        final Mapper mapper : customSerializers.values()) {
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

    /**
     * Registers a (de-/)serializer definition for serializing (parsing)
     * {@link LocalDateTime} as/from {@link String}s.
     * @deprecated A serializer for {@link LocalDateTime} is registered by default.
     */
    @Deprecated(forRemoval = true, since = "5.0.0")
    public static void addLocalDateTimeSerializer() {
        addSerializer(
                new StdSerializer<LocalDateTime>(LocalDateTime.class) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeString(value.toString());

                    }
                }, new StdDeserializer<LocalDateTime>(LocalDateTime.class) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public LocalDateTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
                            JsonProcessingException {
                        JsonNode node = jp.getCodec().readTree(jp);
                        String dt = node.textValue();
                        return LocalDateTime.parse(dt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                }, false);
    }

    /**
     * Registers a custom serializer for a specific object type to the mappers generated with this utility.
     * Does nothing, if a serializer for the same type is already registered.
     *
     * @param <T> Object type for which the serializers should be added
     * @param serializer custom serializer
     * @param deserializer custom deserializer
     */
    public static <T> void addSerializer(JsonSerializer<T> serializer, JsonDeserializer<T> deserializer) {
        addSerializer(serializer, deserializer, false);
    }

    /**
     * Registers a custom serializer for a specific object type to the mappers
     * generated with this utility.
     *
     * @param <T> Object type for which the serializers should be added
     * @param serializer custom serializer
     * @param deserializer custom deserializer
     * @param replace If <code>true</code> any registered serializer for the same type is replaced. Otherwise, if a
     * serializer for the same type is already registered, this method does nothing. Be careful with overwriting default
     * serializers!
     */
    public static synchronized <T> void addSerializer(JsonSerializer<T> serializer, JsonDeserializer<T> deserializer,
            boolean replace) {
        final Class<T> type = serializer.handledType();
        if (!replace && customSerializers.containsKey(type)) {
            LOG.debug("Skip adding new serializer because there is already one registered for type {}", type.getName());
            return;
        }
        LOG.trace("Register new mapper for {}", type.getName());
        defaultMapper = null;
        cachedMapper = null;
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
            defaultMapper = null;
            cachedMapper = null;
            return true;
        }
        return false;
    }

    /**
     * Creates a customized wrapper with some special settings.
     *
     * @param skipNullValues if true all null values will not be serialized.
     * @param pretty if true the result JSON will be prettyfied.
     * @return the ObjectMapper with the selected settings.
     */
    public static ObjectMapper getMapper(boolean skipNullValues, boolean pretty) {
        ObjectMapper om = getDefaultMapper(null);
        om.setSerializationInclusion(skipNullValues ? Include.NON_NULL : Include.ALWAYS);
        om.configure(SerializationFeature.INDENT_OUTPUT, pretty);
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, pretty);
        return om;
    }

    /**
     * Returns a default writer instance. This is essentially the same as
     * invoking {@link #createWriter(boolean, boolean)} with both parameters set
     * to <code>false</code>.
     *
     * @return the DefaultWriter
     */
    public static ObjectWriter createDefaultWriter() {
        return getDefaultMapper().writer();
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
    public static ObjectWriter createWriter(boolean skipNullValues, boolean pretty) {
        ObjectMapper om = getMapper(skipNullValues, pretty);
        return om.writer();
    }

    /**
     * Coverts Properties to json
     *
     * @param properties to convert
     * @return json string representation for the given properties
     */
    public static String propertiesToJsonString(final Properties properties) {
        final ObjectWriter objectWriter = createDefaultWriter();
        final StringWriter stringWriter = new StringWriter();
        try {
            objectWriter.writeValue(stringWriter, properties);
        } catch (final IOException ex) {
            throw new NjamsSdkRuntimeException("Error serializing properties " + properties, ex);
        }
        return stringWriter.toString();
    }
}
