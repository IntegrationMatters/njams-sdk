/*
 * Copyright (c) 2023 Integration Matters GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.http;

import static com.im.njams.sdk.NjamsSettings.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.StringUtils;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;

/**
 * Factory that provides configured http client instances according to the configuration given as properties.
 */
public class HttpClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientFactory.class);

    /**
     * Constant for media type <code>application/json</code>
     */
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private final Interceptor requestAuthenticator;
    private final Authenticator proxyAuthenticator;
    private final Proxy proxy;
    private final SSLContext sslContext;
    private final X509TrustManager trustManager;
    private final HostnameVerifier hostnameVerifier;
    private final boolean compression;

    public HttpClientFactory(final Properties properties, final URI uri) throws Exception {
        requestAuthenticator = createBasicAuth(properties);
        final Entry<Proxy, Authenticator> proxyEntry = createProxy(properties);
        if (proxyEntry != null) {
            proxy = proxyEntry.getKey();
            proxyAuthenticator = proxyEntry.getValue();
        } else {
            proxy = null;
            proxyAuthenticator = null;
        }

        if (isSslUri(uri)) {
            final Entry<SSLContext, X509TrustManager> sslEntry = createSSLContext(properties);
            sslContext = sslEntry.getKey();
            trustManager = sslEntry.getValue();

            // accept all hostnames?
            if ("true".equalsIgnoreCase(properties.getProperty(PROPERTY_HTTP_DISABLE_HOSTNAME_VERIFICATION))) {
                hostnameVerifier = (hostname, session) -> true;
                LOG.warn("Using unsafe option: disable-hostname-verification");
            } else {
                hostnameVerifier = null;
            }
        } else {
            sslContext = null;
            trustManager = null;
            hostnameVerifier = null;
        }

        compression = "true".equalsIgnoreCase(properties.getProperty(PROPERTY_HTTP_COMPRESSION_ENABLED));
    }

    /**
     * Returns whether or not the given URI uses <code>https</code> scheme.
     * @param uri The URI to test
     * @return <code>true</code> only if the given URI uses https
     */
    public static boolean isSslUri(final URI uri) {
        if (uri == null) {
            return false;
        }
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    /**
     * Returns whether or not the given URL uses <code>https</code> scheme.
     * @param url The URL to test
     * @return <code>true</code> only if the given URL uses https
     */
    public static boolean isSslUrl(final URL url) {
        try {
            return isSslUri(url.toURI());
        } catch (final URISyntaxException e) {
            // should not happen
            LOG.debug("Failed to check SSL URL: {}", url, e);
            return false;
        }
    }

    /**
     * Creates a new client instance according to this builder's configuration.
     * @return A new client instance.
     */
    public OkHttpClient createClient() {
        try {
            final okhttp3.OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS);

            if (compression) {
                clientBuilder.addInterceptor(new GzipRequestInterceptor());
            }

            // Basic-Auth for nJAMS ingest; SER-5068
            if (requestAuthenticator != null) {
                // SDK-372: instead of clientBuilder.authenticator(requestAuthenticator) add an interceptor
                clientBuilder.addInterceptor(requestAuthenticator);
            }
            // init proxy authenticator
            if (proxy != null) {
                clientBuilder.proxy(proxy);
                if (proxyAuthenticator != null) {
                    clientBuilder.proxyAuthenticator(proxyAuthenticator);
                }
            }

            // SSL?
            if (sslContext != null) {
                clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
                if (hostnameVerifier != null) {
                    clientBuilder.hostnameVerifier(hostnameVerifier);
                }
            }

            return clientBuilder.build();
        } catch (final Exception e) {
            throw new RuntimeException("Could not initialize http sender", e);
        }

    }

    /**
     * NOTE: Authentication is supported since nJAMS server 6.0.1 only (SER-5068)
     * @param properties
     * @return SDK-372: Instead of building an {@link Authenticator} which leads to duplicated requests for any reason,
     * return an {@link Interceptor} that adds the according <code>Authorization</code> header.
     */
    private Interceptor createBasicAuth(final Properties properties) {
        final String user = properties.getProperty(PROPERTY_HTTP_USER);
        if (StringUtils.isBlank(user)) {
            return null;
        }
        final String credential =
            Credentials.basic(user, properties.getProperty(PROPERTY_HTTP_PASSWORD), StandardCharsets.UTF_8);
        LOG.debug("Created basic-auth with user: {}", user);
        return chain -> chain.proceed(chain.request().newBuilder().header("Authorization", credential).build());
    }

    private Entry<Proxy, Authenticator> createProxy(final Properties properties) {
        final String proxyHost = properties.getProperty(PROPERTY_HTTP_PROXY_HOST);

        // init proxy
        if (StringUtils.isBlank(proxyHost)) {
            return null;
        }
        final int proxyPort = Integer.parseInt(properties.getProperty(PROPERTY_HTTP_PROXY_PORT, "80"));
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));

        // init proxy authenticator
        final String proxyUser = properties.getProperty(PROPERTY_HTTP_PROXY_USER);
        Authenticator proxyAuthenticator = null;
        if (StringUtils.isNotBlank(proxyUser)) {
            final String credential =
                Credentials.basic(proxyUser, properties.getProperty(PROPERTY_HTTP_PROXY_PASSWORD),
                    StandardCharsets.UTF_8);
            proxyAuthenticator = (route, response) -> response.request().newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
        }
        LOG.debug("Created proxy {}:{} (user={})", proxyHost, proxyPort, proxyUser);
        return new SimpleImmutableEntry<>(proxy, proxyAuthenticator);
    }

    private Entry<SSLContext, X509TrustManager> createSSLContext(final Properties properties) throws Exception {

        final SSLContext sslContext = SSLContext.getInstance("SSL");
        TrustManager trustManagers[] = null;
        KeyManager keyManagers[] = null;

        // truststore?
        final String truststorePath = properties.getProperty(PROPERTY_HTTP_TRUSTSTORE_PATH);
        final String certificateFile = properties.getProperty(PROPERTY_HTTP_SSL_CERTIFICATE_FILE);
        if (StringUtils.isNotBlank(truststorePath)) {
            LOG.debug("Load truststore from: {}", truststorePath);
            final String truststoreType = properties.getProperty(PROPERTY_HTTP_TRUSTSTORE_TYPE, "jks");
            final String truststorePassword = properties.getProperty(PROPERTY_HTTP_TRUSTSTORE_PASSWORD);
            final KeyStore truststore = readKeyStore(truststorePath, truststoreType, truststorePassword);
            final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(truststore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } else if (StringUtils.isNotBlank(certificateFile)) {
            // new truststore with cert-file
            LOG.debug("Create truststore with: {}", certificateFile);
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            final Certificate cert;
            try (InputStream fis = new FileInputStream(certificateFile)) {
                cert = certFactory.generateCertificate(fis);
            }
            // load the keystore that includes self-signed cert as a "trusted" entry
            final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustStore.setCertificateEntry("cert-alias", cert);
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } else if ("true".equalsIgnoreCase(properties.getProperty(PROPERTY_HTTP_TRUST_ALL_CERTIFICATES))) {
            LOG.warn("Using unsafe option: trust-all-certificates");
            // accept all certificates
            trustManagers = new TrustManager[] { new X509TrustManager() {

                @Override
                public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                    throws CertificateException {
                }

                @Override
                public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                    throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[] {};
                }
            } };
        } else {
            LOG.debug("Use system default trust-store");
            // get from default SSLContext
            final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
        }

        // keystore?
        final String keystorePath = properties.getProperty(PROPERTY_HTTP_KEYSTORE_PATH);
        if (StringUtils.isNotBlank(keystorePath)) {
            LOG.debug("Load keystore from: {}", keystorePath);
            final String keystoreType = properties.getProperty(PROPERTY_HTTP_KEYSTORE_TYPE, "jks");
            final String keystorePassword = properties.getProperty(PROPERTY_HTTP_KEYSTORE_PASSWORD);
            final KeyStore keystore = readKeyStore(keystorePath, keystoreType, keystorePassword);
            final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keystorePassword.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        sslContext.init(keyManagers, trustManagers, new SecureRandom());

        final X509TrustManager x509TrustManager =
            Arrays.stream(trustManagers).filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast).findAny().orElse(null);
        LOG.debug("Created SSL context.");
        return new SimpleImmutableEntry<>(sslContext, x509TrustManager);
    }

    private KeyStore readKeyStore(final String keystorePath, final String type, final String password)
        throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

        final KeyStore ks = KeyStore.getInstance(type);
        try (final InputStream is = new FileInputStream(keystorePath)) {
            ks.load(is, password.toCharArray());
        }
        return ks;
    }

}
