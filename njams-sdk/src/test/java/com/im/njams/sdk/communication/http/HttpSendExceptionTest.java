package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URL;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link HttpSendException}: message composition and cause propagation for both the
 * URI and URL constructors.
 */
public class HttpSendExceptionTest {

    @Test
    public void uriConstructorBuildsMessageAndKeepsCause() throws Exception {
        URI uri = new URI("http://localhost/ingest");
        Throwable cause = new IllegalStateException("boom");

        HttpSendException ex = new HttpSendException(uri, cause);

        assertTrue(ex.getMessage().contains(uri.toString()));
        assertSame(cause, ex.getCause());
    }

    @Test
    public void urlConstructorBuildsMessageAndKeepsCause() throws Exception {
        URL url = new URL("http://localhost/ingest");
        Throwable cause = new RuntimeException("boom");

        HttpSendException ex = new HttpSendException(url, cause);

        assertTrue(ex.getMessage().contains(url.toString()));
        assertSame(cause, ex.getCause());
    }

    @Test
    public void isNjamsSdkRuntimeException() throws Exception {
        // it must be a NjamsSdkRuntimeException so it triggers receiver reconnect
        HttpSendException ex = new HttpSendException(new URI("http://x"), new RuntimeException());
        assertTrue(ex instanceof NjamsSdkRuntimeException);
    }
}
