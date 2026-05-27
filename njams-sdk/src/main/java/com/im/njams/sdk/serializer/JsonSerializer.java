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
 * Json Serializer
 * @author stkniep
 * @param <T> generic
 */
public class JsonSerializer<T> implements Serializer<T> {

    private final ObjectMapper objectMapper = JsonSerializerFactory.getDefaultMapper();
    private final ObjectWriter objectWriter = this.objectMapper.writer();

    /**
     * Serialize the given object to a JSON string, with no effective size limit.
     *
     * @param object Object to serialize, may be {@code null}
     * @return JSON representation, or {@code "{}"} if {@code object} is {@code null}
     * @throws NjamsSdkRuntimeException if Jackson fails to serialize the object
     */
    @Override
    public String serialize(final T object) throws NjamsSdkRuntimeException {
        if (object == null) {
            return "{}";
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
     * Serialize the given object to a JSON string, stopping near {@code sizeLimit} characters.
     *
     * <p>The returned string may slightly exceed {@code sizeLimit} due to Jackson's internal
     * buffering. {@code sizeLimit <= 0} or {@code sizeLimit == Integer.MAX_VALUE} mean
     * "no limit" and route to the unlimited fast path.</p>
     *
     * @param object    Object to serialize, may be {@code null}
     * @param sizeLimit Approximate maximum length of the returned string
     * @return JSON representation, possibly clipped near {@code sizeLimit}
     * @throws NjamsSdkRuntimeException if Jackson fails for a reason other than the size limit
     */
    @Override
    public String serialize(final T object, final int sizeLimit) throws NjamsSdkRuntimeException {
        if (object == null) {
            return "{}";
        }
        if (sizeLimit <= 0 || sizeLimit == Integer.MAX_VALUE) {
            return serialize(object);
        }
        final StringWriter buffer = new StringWriter();
        try (LimitedWriter limited = new LimitedWriter(buffer, sizeLimit)) {
            objectWriter.writeValue(limited, object);
        } catch (Exception e) {
            if (!containsSizeLimitReached(e)) {
                throw new NjamsSdkRuntimeException("Could not serialize object " + object, e);
            }
        }
        return buffer.toString();
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
