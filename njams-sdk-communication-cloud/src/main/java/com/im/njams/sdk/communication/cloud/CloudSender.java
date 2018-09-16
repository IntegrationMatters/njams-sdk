/* 
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.communication.cloud;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.Sender;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import static java.nio.charset.Charset.defaultCharset;
import java.security.KeyStore;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.LoggerFactory;

/**
 *
 * @author stkniep
 */
public class CloudSender implements Sender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudSender.class);

    public static final String NJAMS_MESSAGETYPE = "x-njams-messagetype";
    public static final String NJAMS_MESSAGEVERSION = "x-njams-messageversion";
    public static final String NJAMS_PATH = "x-njams-path";
    public static final String NJAMS_LOGID = "x-njams-logid";

    private URL url;
    private String apikey;
    private final ObjectMapper mapper;

    public CloudSender() {
        this.mapper = JsonSerializerFactory.getDefaultMapper();
    }

    @Override
    public void init(Properties properties) {
        //TODO: do it for production
//        try {
//            loadKeystore();
//        } catch (final Exception ex) {
//            LOG.error("Error initializing keystore", ex);
//            throw new NjamsSdkRuntimeException("Error initializing keystore", ex);
//        }
        try {
            this.url = new URL(properties.getProperty(CloudConstants.URL));
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
        this.apikey = properties.getProperty(CloudConstants.APIKEY);
    }

    @Override
    public String getName() {
        return CloudConstants.NAME;
    }

    @Override
    public void send(CommonMessage msg) {
        if (msg instanceof LogMessage) {
            send((LogMessage) msg);
        } else if (msg instanceof ProjectMessage) {
            send((ProjectMessage) msg);
        } else {
            // unknown type ... what now?
        }
    }
    
    private void send(final LogMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
        properties.put(NJAMS_PATH, msg.getPath());
        properties.put(NJAMS_LOGID, msg.getLogId());
        try {
            LOG.info("Sending log message");
            final String response = send(msg, properties);
            LOG.info("Response: " + response);
        } catch (Exception ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    private void send(final ProjectMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
        properties.put(NJAMS_PATH, msg.getPath());

        try {
            LOG.info("Sending project message");
            final String response = send(msg, properties);
            LOG.info(response);
        } catch (Exception ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    public void close() {
        // nothing to do here ...
    }

    private void addAddtionalProperties(final Properties properties, final HttpsURLConnection connection) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }

    private String send(final Object msg, final Properties properties) {
        HttpsURLConnection connection = null;

        try {
            //Create connection
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("x-api-key", apikey);

            final String body = mapper.writeValueAsString(msg);
            connection.setRequestProperty("Content-Length",
                    Integer.toString(body.getBytes("UTF-8").length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            connection.setRequestProperty(NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
            addAddtionalProperties(properties, connection);

            connection.getRequestProperties().entrySet().forEach(e -> LOG.debug("Header {} : {}", e.getKey(), e.getValue()));

            //Send request
            try (final DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(body);
            }
            LOG.debug("Send msg {}", body);

            //Get Response
            final InputStream is = connection.getInputStream();
            final StringBuilder response;
            try (final BufferedReader rd = new BufferedReader(new InputStreamReader(is, defaultCharset()))) {
                response = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
            }
            final int responseCode = connection.getResponseCode();
            return new StringBuilder("rc = ")
                    .append(responseCode)
                    .append(", logId=")
                    .append('"')
                    .append(response)
                    .append('"')
                    .toString();
        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Error sending message", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void loadKeystore() throws IOException {
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            try (InputStream keystoreInput
                    = Thread.currentThread().getContextClassLoader().getResourceAsStream("client.ks");
                    InputStream truststoreInput
                    = Thread.currentThread().getContextClassLoader().getResourceAsStream("client.ts")) {
                setSSLFactories(keystoreInput, "password", truststoreInput);
            }
        } else {
            LOG.debug("***      nJAMS: using provided keystore" + System.getProperty("javax.net.ssl.trustStore"));
        }
    }

    private static void setSSLFactories(final InputStream keyStream, final String keyStorePassword,
            final InputStream trustStream) {

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
    
    @Override
    public String getPropertyPrefix() {
        return CloudConstants.PROPERTY_PREFIX;
    }
}
