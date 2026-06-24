package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import okhttp3.Interceptor.Chain;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * Unit tests for {@link GzipRequestInterceptor}: pass-through cases and the GZIP wrapping of the
 * request body.
 */
public class GzipRequestInterceptorTest {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static Response responseFor(Request request) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build();
    }

    @Test
    public void requestWithoutBodyIsForwardedUnchanged() throws Exception {
        Request original = new Request.Builder().url("http://localhost").get().build();
        Chain chain = mock(Chain.class);
        when(chain.request()).thenReturn(original);
        when(chain.proceed(original)).thenReturn(responseFor(original));

        new GzipRequestInterceptor().intercept(chain);

        verify(chain).proceed(original);
    }

    @Test
    public void requestAlreadyEncodedIsForwardedUnchanged() throws Exception {
        Request original = new Request.Builder().url("http://localhost")
                .header("Content-Encoding", "gzip")
                .post(RequestBody.create("data", JSON))
                .build();
        Chain chain = mock(Chain.class);
        when(chain.request()).thenReturn(original);
        when(chain.proceed(original)).thenReturn(responseFor(original));

        new GzipRequestInterceptor().intercept(chain);

        verify(chain).proceed(original);
    }

    @Test
    public void requestBodyIsGzipCompressed() throws Exception {
        Request original = new Request.Builder().url("http://localhost")
                .post(RequestBody.create("hello-gzip", JSON))
                .build();
        Chain chain = mock(Chain.class);
        when(chain.request()).thenReturn(original);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        when(chain.proceed(captor.capture())).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        new GzipRequestInterceptor().intercept(chain);

        Request compressed = captor.getValue();
        assertEquals("gzip", compressed.header("Content-Encoding"));

        RequestBody body = compressed.body();
        // compressed length is unknown in advance
        assertEquals(-1, body.contentLength());
        // content type is delegated to the original body
        assertSame(JSON, body.contentType());

        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        byte[] gzipped = buffer.readByteArray();
        // GZIP magic header
        assertEquals((byte) 0x1f, gzipped[0]);
        assertEquals((byte) 0x8b, gzipped[1]);
        // and it round-trips back to the original payload
        assertEquals("hello-gzip", gunzip(gzipped));
    }

    private static String gunzip(byte[] data) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
