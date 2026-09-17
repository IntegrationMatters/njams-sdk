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

import java.io.IOException;
import java.io.Writer;

/**
 * Writer wrapper that aborts once a fixed number of characters have been written. Shared by the
 * size-limited serializers so a large object is never fully materialised before being clipped.
 */
final class LimitedWriter extends Writer {
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

    /**
     * Returns {@code true} if the given throwable chain was caused by reaching the size limit, i.e.
     * the output was truncated rather than failing for another reason.
     */
    static boolean isSizeLimitReached(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SizeLimitReached) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
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
