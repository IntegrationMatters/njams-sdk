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
