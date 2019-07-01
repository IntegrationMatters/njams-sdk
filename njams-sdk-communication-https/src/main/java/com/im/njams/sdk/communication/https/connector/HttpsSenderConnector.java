/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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

package com.im.njams.sdk.communication.https.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.https.HttpsConstants;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.Charset.defaultCharset;
/**
 * Todo: write doc
 */
public class HttpsSenderConnector extends HttpsConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpsSenderConnector.class);

    protected String user;
    protected String password;

    protected URL url;

    public HttpsSenderConnector(Properties properties, String name) {
        super(properties, name);

        user = properties.getProperty(HttpsConstants.SENDER_USERNAME);
        password = properties.getProperty(HttpsConstants.SENDER_PASSWORD);
        try {
            url = new URL(properties.getProperty(HttpsConstants.SENDER_URL));
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init https sender", ex);
        }
        try {
            loadKeystore();
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Error setting up HttpsSender", ex);
        }
    }

    public URL getUrl(int utf8Bytes, Properties properties) throws IOException {
        return url;
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        return exceptions;
    }

    protected void loadKeystore() throws IOException {
        if (System.getProperty(HttpsConstants.TRUST_STORE) == null) {
            try (InputStream keystoreInput
                         = Thread.currentThread().getContextClassLoader().getResourceAsStream(HttpsConstants.CLIENT_KS);
                 InputStream truststoreInput
                         = Thread.currentThread().getContextClassLoader().getResourceAsStream(HttpsConstants.CLIENT_TS)) {
                setSSLFactories(keystoreInput, "password", truststoreInput);
            }
        } else {
            LOG.debug("***      nJAMS: using provided keystore {}", System.getProperty(HttpsConstants.TRUST_STORE));
        }
    }

    private static void setSSLFactories(final InputStream keyStream, final String keyStorePassword, final InputStream trustStream) {

        try {
            // Get keyStore
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            // if your store is password protected then declare it (it can be null however)
            final char[] keyPassword = keyStorePassword.toCharArray();

            // load the stream to your store
            keyStore.load(keyStream, keyPassword);

            // initialize a trust manager factory with the trusted store
            final KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keyStore, keyPassword);

            // get the trust managers from the factory
            final KeyManager[] keyManagers = keyFactory.getKeyManagers();

            // Now get trustStore
            final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

            // if your store is password protected then declare it (it can be null however)
            // char[] trustPassword = password.toCharArray();
            // load the stream to your store
            trustStore.load(trustStream, null);

            // initialize a trust manager factory with the trusted store
            final TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustFactory.init(trustStore);

            // get the trust managers from the factory
            final TrustManager[] trustManagers = trustFactory.getTrustManagers();

            // initialize an ssl context to use these managers and set as default
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(keyManagers, trustManagers, null);
            SSLContext.setDefault(sslContext);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to set up SSL environment", ex);
        }
    }

    public HttpURLConnection getConnection() throws IOException {
        //Create connection
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        setRequestProperties(connection);

        setUser(connection);
        return connection;
    }

    private void setUser(HttpURLConnection connection){
        if (user != null) {
            final Base64.Encoder encoder = Base64.getEncoder();
            final String userpassword = user + ":" + password;
            final byte[] encodedAuthorization = encoder.encode(userpassword.getBytes(defaultCharset()));
            connection.setRequestProperty("Authorization",
                    "Basic " + new String(encodedAuthorization, defaultCharset()));
        }
    }

    private final void setRequestProperties(HttpURLConnection connection) throws ProtocolException {
        connection.setRequestMethod(HttpsConstants.HTTP_REQUEST_TYPE_POST);
        connection.setRequestProperty(HttpsConstants.CONTENT_TYPE, HttpsConstants.CONTENT_TYPE_JSON + ", " + HttpsConstants.UTF_8);
        connection.setRequestProperty(HttpsConstants.ACCEPT, HttpsConstants.CONTENT_TYPE_TEXT);
        connection.setRequestProperty(HttpsConstants.CONTENT_LANGUAGE, HttpsConstants.CONTENT_LANGUAGE_EN_US);

        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("x-njams-type", "keep-alive");

        connection.setUseCaches(false);
        connection.setDoOutput(true);
    }

    @Override
    public void connect() {
        //Do nothing, a connection has to be established with each send.
    }
}
