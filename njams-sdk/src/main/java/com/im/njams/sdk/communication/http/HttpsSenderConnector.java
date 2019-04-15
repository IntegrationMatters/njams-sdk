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

package com.im.njams.sdk.communication.http;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.util.Properties;

public class HttpsSenderConnector extends HttpSenderConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpsSenderConnector.class);

    public HttpsSenderConnector(Properties properties, String name) {
        super(properties, name);
        try {
            loadKeystore();
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Error setting up HttpsSender", ex);
        }
    }

    private void loadKeystore() throws Exception {
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            InputStream truststoreInput;
            try (InputStream keystoreInput =
                         Thread.currentThread().getContextClassLoader().getResourceAsStream("client.ks")) {
                truststoreInput = Thread.currentThread().getContextClassLoader().getResourceAsStream("client.ts");
                setSSLFactories(keystoreInput, "password", truststoreInput);
            }
            truststoreInput.close();
        } else {
            LOG.debug("***      nJAMS: using provided keystore" + System.getProperty("javax.net.ssl.trustStore"));
        }
    }

    private static void setSSLFactories(final InputStream keyStream, final String keyStorePassword,
                                        final InputStream trustStream)
            throws Exception {
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
        final TrustManagerFactory trustFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);

        // get the trust managers from the factory
        final TrustManager[] trustManagers = trustFactory.getTrustManagers();

        // initialize an ssl context to use these managers and set as default
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagers, trustManagers, null);
        SSLContext.setDefault(sslContext);
    }

    @Override
    public HttpURLConnection getConnection() throws IOException {
        //Create connection
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        super.setDefaultRequestProperties(connection);
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("x-njams-type", "keep-alive");

        super.setUser(connection);
        return connection;
    }
}
