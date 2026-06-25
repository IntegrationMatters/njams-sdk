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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * {@link Serializer} implementation that converts objects to their JSON string representation
 * using Jackson.
 *
 * <p>By default, the serializer produces compact output. Pass {@code true} to
 * {@link #JsonSerializer(boolean)} to obtain indented, human-readable output instead.</p>
 *
 * <p>Both {@code serialize} methods return {@code null} when the object is {@code null}.</p>
 *
 * @param <T> the type of object to serialize
 */
public class JsonSerializer<T> implements Serializer<T> {

    private final ObjectWriter objectWriter;

    /**
     * Creates a serializer that produces compact (non-pretty-printed) JSON output.
     */
    public JsonSerializer() {
        this(false);
    }

    /**
     * Creates a serializer with the given pretty-printing setting.
     *
     * <p>When {@code pretty} is {@code true}, the default mapper from
     * {@link JsonSerializerFactory#getDefaultMapper()} is used, which produces indented,
     * human-readable JSON with entries ordered by key. When {@code false}, the fast mapper
     * from {@link JsonSerializerFactory#getFastMapper()} is used, producing compact output
     * optimized for performance.</p>
     *
     * @param pretty {@code true} for indented, human-readable JSON; {@code false} for compact output
     */
    // @Deprecated flags external API consumers only; internal use of Jackson factory is intentional.
    @SuppressWarnings("deprecation")
    public JsonSerializer(final boolean pretty) {
        final ObjectMapper mapper = pretty
                ? JsonSerializerFactory.getDefaultMapper()
                : JsonSerializerFactory.getFastMapper();
        this.objectWriter = mapper.writer();
    }

    /**
     * Serialize the given object to a JSON string, with no effective size limit.
     *
     * @param object Object to serialize, may be {@code null}
     * @return JSON representation, or {@code null} if {@code object} is {@code null}
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
     * Serialize the given object to a JSON string, stopping near {@code sizeLimit} characters and
     * reporting whether the output was truncated.
     *
     * <p>The value may slightly exceed {@code sizeLimit} due to Jackson's internal buffering.
     * {@code sizeLimit <= 0} or {@code sizeLimit == Integer.MAX_VALUE} mean "no limit" and route to
     * the unlimited fast path (never truncated). Otherwise the stream is aborted once the limit is
     * reached, and {@link SerializerResult#truncated()} is {@code true} exactly when that happened
     * (i.e. the object was larger than {@code sizeLimit}).</p>
     *
     * @param object    Object to serialize, may be {@code null}
     * @param sizeLimit Approximate maximum length of the produced string
     * @return the JSON value (possibly clipped) and its truncation flag, or {@code null} if
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
            if (containsSizeLimitReached(e)) {
                truncated = true;
            } else {
                throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
            }
        }
        return new SerializerResult(buffer.toString(), truncated);
    }

    private static boolean containsSizeLimitReached(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof LimitedWriter.SizeLimitReached) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Writer wrapper that aborts once a fixed number of characters have been written.
     */
    private static final class LimitedWriter extends Writer {
        private final Writer delegate;
        private final int limit;
        private int written;

        LimitedWriter(Writer delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (written >= limit) {
                throw new SizeLimitReached();
            }
            int allowed = Math.min(len, limit - written);
            delegate.write(cbuf, off, allowed);
            written += allowed;
            if (allowed < len) {
                throw new SizeLimitReached();
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /** Sentinel thrown when the size limit has been reached. */
        static final class SizeLimitReached extends IOException {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable fillInStackTrace() {
                // control-flow signal, not a real error: skip the costly stack walk
                return this;
            }
        }
    }
}
