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
package com.im.njams.sdk.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

import java.io.InputStream;

/**
 * Utilities for serializing to and parsing from XML.
 *
 * <p>Serialized output contains no XML prolog ({@code <?xml ...?>}); the root element name follows
 * Jackson's XML data-format conventions (the class name unless overridden by annotations).</p>
 */
public class XmlUtils {

    private static final XmlMapper COMPACT_MAPPER = createXmlMapper(false);
    private static final XmlMapper PRETTY_MAPPER = createXmlMapper(true);

    private XmlUtils() {
        // static only
    }

    /**
     * Parses the given XML string and initializes a new object of the given type from that XML.
     *
     * @param <T>  The type of the object to return
     * @param xml  The XML string to parse.
     * @param type The type of the object to be created from the given XML.
     * @return New instance of the given type.
     * @throws NjamsSdkRuntimeException If parsing the given XML into the target type failed.
     */
    public static <T> T parse(String xml, Class<T> type) throws NjamsSdkRuntimeException {
        try {
            return COMPACT_MAPPER.readValue(xml, type);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException(
                "Could not parse XML string " + xml + " to type " + type.getSimpleName(), e);
        }
    }

    /**
     * Parses the given InputStream and initializes a new object of the given type from that InputStream.
     *
     * @param <T>    The type of the object to return
     * @param stream The InputStream to parse.
     * @param type   The type of the object to be created from the given XML.
     * @return New instance of the given type.
     * @throws NjamsSdkRuntimeException If parsing the given XML into the target type failed.
     */
    public static <T> T parse(InputStream stream, Class<T> type) throws NjamsSdkRuntimeException {
        try {
            return COMPACT_MAPPER.readValue(stream, type);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException(
                "Could not parse InputStream to type " + type.getSimpleName(), e);
        }
    }

    /**
     * Serializes the given object to an XML string.
     *
     * @param object The object to serialize.
     * @return XML string representing the given object.
     * @throws NjamsSdkRuntimeException If serializing the object to XML failed.
     */
    public static String serialize(Object object) throws NjamsSdkRuntimeException {
        return serialize(object, false);
    }

    /**
     * Serializes the given object to an XML string.
     *
     * @param object      The object to serialize.
     * @param prettyPrint If true produces indented, human-readable XML, otherwise compact XML.
     * @return XML string representing the given object.
     * @throws NjamsSdkRuntimeException If serializing the object to XML failed.
     */
    public static String serialize(Object object, boolean prettyPrint) throws NjamsSdkRuntimeException {
        try {
            return (prettyPrint ? PRETTY_MAPPER : COMPACT_MAPPER).writeValueAsString(object);
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Could not serialize Object " + object, e);
        }
    }

    /**
     * Builds the configured XML mapper. Kept private (and intentionally mirrored in
     * {@code com.im.njams.sdk.serializer.XmlSerializer}) because {@link XmlMapper} is a Jackson type
     * relocated during packaging and must not be shared across packages on any exposed API surface.
     */
    private static XmlMapper createXmlMapper(final boolean pretty) {
        final XmlMapper mapper = new XmlMapper();
        final AnnotationIntrospector jackson = new JacksonAnnotationIntrospector();
        final AnnotationIntrospector jaxb = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        final AnnotationIntrospector jakarta = new JakartaXmlBindAnnotationIntrospector(mapper.getTypeFactory());
        mapper.setAnnotationIntrospector(
            AnnotationIntrospector.pair(AnnotationIntrospector.pair(jackson, jaxb), jakarta));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, pretty);
        return mapper;
    }
}
