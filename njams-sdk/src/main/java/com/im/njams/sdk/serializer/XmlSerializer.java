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
package com.im.njams.sdk.serializer;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

import java.io.StringWriter;

/**
 * {@link Serializer} implementation that converts objects to their XML string representation
 * using Jackson's XML data format.
 *
 * <p>By default, the serializer produces compact output. Pass {@code true} to
 * {@link #XmlSerializer(boolean)} to obtain indented, human-readable output instead. No XML prolog
 * ({@code <?xml ...?>}) is emitted.</p>
 *
 * <p>Both {@code serialize} methods return {@code null} when the object is {@code null}.</p>
 *
 * @param <T> the type of object to serialize
 */
public class XmlSerializer<T> implements Serializer<T> {

    private static final XmlMapper COMPACT_MAPPER = createXmlMapper(false);
    private static final XmlMapper PRETTY_MAPPER = createXmlMapper(true);

    private final ObjectWriter objectWriter;

    /**
     * Creates a serializer that produces compact (non-pretty-printed) XML output.
     */
    public XmlSerializer() {
        this(false);
    }

    /**
     * Creates a serializer with the given pretty-printing setting.
     *
     * <p>When {@code pretty} is {@code true}, the output is indented, human-readable XML; when
     * {@code false}, compact output optimized for performance is produced. No XML prolog is emitted
     * in either case.</p>
     *
     * @param pretty {@code true} for indented, human-readable XML; {@code false} for compact output
     */
    public XmlSerializer(final boolean pretty) {
        this.objectWriter = (pretty ? PRETTY_MAPPER : COMPACT_MAPPER).writer();
    }

    /**
     * Serialize the given object to an XML string, with no effective size limit.
     *
     * @param object Object to serialize, may be {@code null}
     * @return XML representation, or {@code null} if {@code object} is {@code null}
     * @throws NjamsSdkRuntimeException if Jackson fails to serialize the object
     */
    @Override
    public String serialize(final T object) throws NjamsSdkRuntimeException {
        if (object == null) {
            return null;
        }
        try {
            final StringWriter writer = new StringWriter();
            objectWriter.writeValue(writer, object);
            return writer.toString();
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
        }
    }

    /**
     * Serialize the given object to an XML string, stopping near {@code sizeLimit} characters and
     * reporting whether the output was truncated.
     *
     * <p>The value may slightly exceed {@code sizeLimit} due to internal buffering. {@code sizeLimit
     * <= 0} or {@code sizeLimit == Integer.MAX_VALUE} mean "no limit" and route to the unlimited fast
     * path (never truncated). Otherwise the stream is aborted once the limit is reached, and
     * {@link SerializerResult#truncated()} is {@code true} exactly when that happened (i.e. the object
     * was larger than {@code sizeLimit}). A truncated value may be incomplete and therefore not
     * well-formed XML.</p>
     *
     * @param object    Object to serialize, may be {@code null}
     * @param sizeLimit Approximate maximum length of the produced string
     * @return the XML value (possibly clipped) and its truncation flag, or {@code null} if
     *         {@code object} is {@code null}
     * @throws NjamsSdkRuntimeException if Jackson fails for a reason other than the size limit
     */
    @Override
    public SerializerResult serialize(final T object, final int sizeLimit) throws NjamsSdkRuntimeException {
        if (object == null) {
            return null;
        }
        if (sizeLimit <= 0 || sizeLimit == Integer.MAX_VALUE) {
            return new SerializerResult(serialize(object), false);
        }
        final StringWriter buffer = new StringWriter();
        boolean truncated = false;
        try (LimitedWriter limited = new LimitedWriter(buffer, sizeLimit)) {
            objectWriter.writeValue(limited, object);
        } catch (Exception e) {
            if (LimitedWriter.isSizeLimitReached(e)) {
                truncated = true;
            } else {
                throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
            }
        }
        return new SerializerResult(buffer.toString(), truncated);
    }

    /**
     * Builds the configured XML mapper. Kept private (and intentionally mirrored in
     * {@code com.im.njams.sdk.utils.XmlUtils}) because {@link XmlMapper} is a Jackson type relocated
     * during packaging and must not be shared across packages on any exposed API surface.
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
