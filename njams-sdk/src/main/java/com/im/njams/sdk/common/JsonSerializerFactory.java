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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.im.njams.sdk.Njams;
import org.slf4j.LoggerFactory;

/**
 * Provides factory methods for JSON serializers.
 *
 * @author cwinkler
 *
 */
public class JsonSerializerFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JsonSerializerFactory.class);

    /**
     * ID for the filter used by the mix-in interface.
     */
    protected static final String MIX_IN_FILTER_ID = "MixInFilter";

    private static final SimpleModule customSerializersModule = new SimpleModule();
    private static final Set<Class<?>> customSerializers = new HashSet<>();

    private JsonSerializerFactory() {
    }

    /**
     * Returns a base mapper with configuration that is required for all created
     * serializers. skipNullValues and pretty will be set to true.
     *
     * @return the ObjectMapper.
     */
    public static ObjectMapper getDefaultMapper() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Jackson databind: {}", com.fasterxml.jackson.databind.ObjectMapper.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson core: {}", com.fasterxml.jackson.core.JsonFactory.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson module.jaxb: {}", com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector.class.getProtectionDomain().getCodeSource().getLocation());
            LOG.trace("Jackson annotation: {}", com.fasterxml.jackson.annotation.JsonInclude.Include.class.getProtectionDomain().getCodeSource().getLocation());
        }
        return getMapper(true, true);
    }

    /**
     * Allows to create a mapper with a different {@link JsonFactory} that uses
     * the same settings as the default JSON mappers used by this application.
     *
     * @param factory May be <code>null</code> to use the default JSON factors.
     * @return the default ObjectMapper
     */
    public static ObjectMapper getDefaultMapper(JsonFactory factory) {
        ObjectMapper om = factory == null ? new ObjectMapper() : new ObjectMapper(factory);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(om.getTypeFactory());
        AnnotationIntrospector pair = AnnotationIntrospector.pair(primary, secondary);
        om.setAnnotationIntrospector(pair);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        om.registerModule(customSerializersModule);
        return om;
    }

    /**
     * Adds a (de-/)serializer definition for serializing (parsing)
     * {@link LocalDateTime} as/from {@link String}s.
     */
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
        });
    }

    /**
     * Adds a custom serializer for a specific object type to the mappers
     * generated with this utility.
     *
     * @param <T> Object type for which the serializers should be added
     * @param serializer custom serializer
     * @param deserializer custom deserializer
     */
    public static synchronized <T> void addSerializer(JsonSerializer<T> serializer,
            JsonDeserializer<T> deserializer) {
        Class<T> type = serializer.handledType();
        if (customSerializers.contains(type)) {
            return;
        }
        customSerializers.add(type);
        customSerializersModule.addSerializer(type, serializer);
        customSerializersModule.addDeserializer(type, deserializer);
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
