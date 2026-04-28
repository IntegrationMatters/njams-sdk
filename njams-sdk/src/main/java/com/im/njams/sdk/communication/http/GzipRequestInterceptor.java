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
package com.im.njams.sdk.communication.http;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/**
 * Http request interceptor that GZIPs request body content. This interceptor is optionally added to the http client
 * if configured via the client's settings. GZIP has to be supported by nJAMS server (since 6.1.2).
 */
public class GzipRequestInterceptor implements Interceptor {
    private static class GzipRequestBody extends RequestBody {
        private final RequestBody originalBody;

        public GzipRequestBody(RequestBody originalBody) {
            this.originalBody = Objects.requireNonNull(originalBody);
        }

        @Override
        public MediaType contentType() {
            return originalBody.contentType();
        }

        @Override
        public long contentLength() {
            return -1; // We don't know the compressed length in advance
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (BufferedSink gzipSink = Okio.buffer(new GzipSink(sink))) {
                originalBody.writeTo(gzipSink);
                gzipSink.flush();
            }
        }
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        if (originalRequest.body() == null || originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest);
        }

        Request compressedRequest = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method(), new GzipRequestBody(originalRequest.body()))
            .build();

        return chain.proceed(compressedRequest);
    }
}
