package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link HttpStatusException}: message composition and status code retention.
 */
public class HttpStatusExceptionTest {

    @Test
    public void constructorBuildsMessageAndKeepsStatusCode() throws Exception {
        URL url = new URL("http://localhost/ingest");

        HttpStatusException ex = new HttpStatusException(url, 503);

        assertTrue(ex.getMessage().contains(url.toString()));
        assertTrue(ex.getMessage().contains("503"));
        assertEquals(503, ex.getStatusCode());
    }

    @Test
    public void isNjamsSdkRuntimeException() throws Exception {
        HttpStatusException ex = new HttpStatusException(new URL("http://x"), 500);
        assertTrue(ex instanceof NjamsSdkRuntimeException);
    }
}
