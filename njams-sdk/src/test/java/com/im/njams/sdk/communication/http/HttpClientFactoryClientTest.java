package com.im.njams.sdk.communication.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * Unit tests for {@link HttpClientFactory#createClient()} and its scheme helpers, covering the
 * compression, basic-auth and proxy configuration branches.
 */
public class HttpClientFactoryClientTest {

    private static HttpClientFactory factory(Map<String, String> props, String uri) throws Exception {
        return new HttpClientFactory(ClientSettings.from(new HashMap<>(props)), new URI(uri));
    }

    @Test
    public void isSslUriDetectsHttpsScheme() throws Exception {
        assertFalse(HttpClientFactory.isSslUri(null));
        assertFalse(HttpClientFactory.isSslUri(new URI("http://localhost")));
        assertTrue(HttpClientFactory.isSslUri(new URI("https://localhost")));
        assertTrue(HttpClientFactory.isSslUri(new URI("HTTPS://localhost")));
    }

    @Test
    public void isSslUrlDetectsHttpsScheme() throws Exception {
        assertTrue(HttpClientFactory.isSslUrl(new URL("https://localhost")));
        assertFalse(HttpClientFactory.isSslUrl(new URL("http://localhost")));
    }

    @Test
    public void mediaTypeJsonIsJson() {
        assertNotNull(HttpClientFactory.MEDIA_TYPE_JSON);
        assertTrue("json".equalsIgnoreCase(HttpClientFactory.MEDIA_TYPE_JSON.subtype()));
    }

    @Test
    public void defaultClientHasNoInterceptorsOrProxy() throws Exception {
        OkHttpClient client = factory(new HashMap<>(), "http://localhost").createClient();
        assertNotNull(client);
        assertTrue(client.interceptors().isEmpty());
        assertNull(client.proxy());
    }

    @Test
    public void compressionAddsGzipInterceptor() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_COMPRESSION_ENABLED, "true");
        OkHttpClient client = factory(props, "http://localhost").createClient();

        assertTrue(client.interceptors().stream().anyMatch(GzipRequestInterceptor.class::isInstance));
    }

    @Test
    public void basicAuthAddsInterceptor() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_USER, "user");
        props.put(NjamsSettings.PROPERTY_HTTP_PASSWORD, "secret");
        OkHttpClient client = factory(props, "http://localhost").createClient();

        // exactly one interceptor (the basic-auth one) and it is not the gzip interceptor
        assertTrue(client.interceptors().size() == 1);
        assertFalse(client.interceptors().stream().anyMatch(GzipRequestInterceptor.class::isInstance));
    }

    @Test
    public void compressionAndAuthAddTwoInterceptors() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_COMPRESSION_ENABLED, "true");
        props.put(NjamsSettings.PROPERTY_HTTP_USER, "user");
        props.put(NjamsSettings.PROPERTY_HTTP_PASSWORD, "secret");
        OkHttpClient client = factory(props, "http://localhost").createClient();

        assertTrue(client.interceptors().size() == 2);
    }

    @Test
    public void proxyHostConfiguresProxy() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_HTTP_PROXY_HOST, "proxy.example.com");
        props.put(NjamsSettings.PROPERTY_HTTP_PROXY_PORT, "8888");
        OkHttpClient client = factory(props, "http://localhost").createClient();

        assertNotNull(client.proxy());
    }

    @Test
    public void interceptorsAreEmptyByDefaultForHttpsToo() throws Exception {
        // ensure SSL setup (https URI) does not accidentally add interceptors
        OkHttpClient client = factory(new HashMap<>(), "https://localhost").createClient();
        assertTrue(client.interceptors().isEmpty());
    }
}
