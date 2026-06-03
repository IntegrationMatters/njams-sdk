package com.im.njams.sdk.communication.http;

import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_TRUST_ALL_CERTIFICATES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;

import com.im.njams.sdk.settings.ClientSettings;

public class HttpClientFactoryTest {

    @SuppressWarnings("unchecked")
    private Entry<SSLContext, X509TrustManager> invokeCreateSSLContext(final Map<String, String> props)
        throws Exception {
        // Use a non-SSL URI so the constructor does not invoke createSSLContext itself; we drive it directly.
        final HttpClientFactory factory =
            new HttpClientFactory(ClientSettings.from(new HashMap<>(props)), new URI("http://localhost"));
        final Method method = HttpClientFactory.class.getDeclaredMethod("createSSLContext", ClientSettings.class);
        method.setAccessible(true);
        return (Entry<SSLContext, X509TrustManager>) method.invoke(factory, ClientSettings.from(new HashMap<>(props)));
    }

    @Test
    public void createSSLContextUsesTlsProtocol() throws Exception {
        final Entry<SSLContext, X509TrustManager> entry = invokeCreateSSLContext(new HashMap<>());
        assertEquals("TLS", entry.getKey().getProtocol());
    }

    @Test
    public void createSSLContextWithDefaultTruststoreProvidesTrustManager() throws Exception {
        final Entry<SSLContext, X509TrustManager> entry = invokeCreateSSLContext(new HashMap<>());
        assertNotNull("expected a trust manager from the system default truststore", entry.getValue());
    }

    @Test
    public void createSSLContextWithTrustAllCertificatesAcceptsAnyCertificate() throws Exception {
        final Map<String, String> props = new HashMap<>();
        props.put(PROPERTY_HTTP_TRUST_ALL_CERTIFICATES, "true");
        final Entry<SSLContext, X509TrustManager> entry = invokeCreateSSLContext(props);
        final X509TrustManager trustManager = entry.getValue();
        assertNotNull(trustManager);
        assertEquals(0, trustManager.getAcceptedIssuers().length);
        // The trust-all manager must accept any chain without throwing.
        trustManager.checkServerTrusted(new X509Certificate[0], "RSA");
    }
}
