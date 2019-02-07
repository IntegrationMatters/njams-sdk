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

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.utils.JsonUtils;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
public class CloudSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudSender.class);

    public static final String NJAMS_MESSAGETYPE = "x-njams-messagetype";
    public static final String NJAMS_MESSAGEVERSION = "x-njams-messageversion";
    public static final String NJAMS_PATH = "x-njams-path";
    public static final String NJAMS_LOGID = "x-njams-logid";

    public static final int FALLBACK_MAX_PAYLOAD_BYTES = 10485760;
    private int maxPayloadBytes;

    private URL url;
    private String apikey;

    @Override
    public void init(Properties properties) {
        //TODO: do it for production
        //        try {
        //            loadKeystore();
        //        } catch (final Exception ex) {
        //            LOG.error("Error initializing keystore", ex);
        //            throw new NjamsSdkRuntimeException("Error initializing keystore", ex);
        //        }
        String apikeypath = properties.getProperty(CloudConstants.APIKEY);

        if (apikeypath == null) {
            LOG.error("Please provide property {} for CloudSender", CloudConstants.APIKEY);
        }

        try {
            maxPayloadBytes = Integer.parseInt(properties.getProperty(CloudConstants.MAX_PAYLOAD_BYTES));
            LOG.debug("maxPayloadBytes: {} Bytes", maxPayloadBytes);
        } catch (Exception e) {
            LOG.warn("Invalid value for maxPayloadBytes, fallback to {} Bytes", FALLBACK_MAX_PAYLOAD_BYTES);
            maxPayloadBytes = 10485760;
        }

        if (maxPayloadBytes > FALLBACK_MAX_PAYLOAD_BYTES) {
            LOG.warn("The value for maxPayloadBytes: {} is too great, fallback to {} Bytes", maxPayloadBytes, FALLBACK_MAX_PAYLOAD_BYTES);
        }
        
        try {
            apikey = ApiKeyReader.getApiKey(apikeypath);
        } catch (Exception e) {
            LOG.error("Failed to load api key from file " + apikeypath, e);
            throw new IllegalStateException("Failed to load api key from file");
        }

        try {
            url = new URL(getIngestEndpoint(properties.getProperty(CloudConstants.ENDPOINT)));
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
    }

    @Override
    public String getName() {
        return CloudConstants.NAME;
    }

    @Override
    protected void send(final LogMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
        properties.put(NJAMS_PATH, msg.getPath());
        properties.put(NJAMS_LOGID, msg.getLogId());
        try {
            LOG.trace("Sending log message");
            final String body = JsonUtils.serialize(msg);

            byte[] byteBody = body.getBytes("UTF-8");
            int utf8Bytes = byteBody.length;
            LOG.debug("Message size in Bytes: {}", utf8Bytes);
            if (utf8Bytes > maxPayloadBytes) {
                LOG.debug("Message exceeds Byte limit: {}/{}", utf8Bytes, maxPayloadBytes);
                URL presignedUrl = getPresignedUrl(properties);
                send(body, presignedUrl);

            } else {
                final String response = send(body, properties);
                LOG.trace("Response: " + response);
            }

        } catch (Exception ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    protected void send(final ProjectMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
        properties.put(NJAMS_PATH, msg.getPath());

        try {
            LOG.trace("Sending project message");
            final String body = JsonUtils.serialize(msg);
            
            byte[] byteBody = body.getBytes("UTF-8");
            int utf8Bytes = byteBody.length;
            LOG.debug("Message size in Bytes: {}", utf8Bytes);
            if (utf8Bytes > maxPayloadBytes) {
                LOG.debug("Message exceeds Byte limit: {}/{}", utf8Bytes, maxPayloadBytes);
                URL presignedUrl = getPresignedUrl(properties);
                send(body, presignedUrl);

            } else {
                final String response = send(body, properties);
                LOG.trace("Response: " + response);
            }
            
        } catch (Exception ex) {
            LOG.error("Error sending ProjectMessage", ex);
        }
    }

     /**
     * @return the ingest endpoint
     */
    protected String getIngestEndpoint(String endpoint) throws Exception {
        String endpointUrl = "https://" + endpoint.trim() + "/v1/endpoints";

        URL url = new URL(endpointUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("x-api-key", apikey);

        int responseCode = connection.getResponseCode();
        LOG.debug("\nSending 'GET' request to URL : " + url);
        LOG.debug("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        Endpoints endpoints = JsonUtils.parse(response.toString(), Endpoints.class);

        return endpoints.ingest.startsWith("https://") ? endpoints.ingest : "https://" + endpoints.ingest;
    }
    
     /**
     * @return a presignedUrl
     */
    protected URL getPresignedUrl(final Properties properties) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("x-api-key", apikey);
        connection.setRequestProperty(NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        addAddtionalProperties(properties, connection);

        connection.getRequestProperties().entrySet().forEach(e -> LOG.debug("Header {} : {}", e.getKey(), e.getValue()));
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        LOG.debug("response: {}", response.toString());

        PresignedUrl presignedUrl = JsonUtils.parse(response.toString(), PresignedUrl.class);
        LOG.debug("presignedUrl: {}", presignedUrl.url);
        return new URL(presignedUrl.url);
    }

    private void addAddtionalProperties(final Properties properties, final HttpsURLConnection connection) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }

    private void send(final String body, final URL presignedUrl) {
        HttpsURLConnection connection = null;

        try {
            //Create connection
            connection = (HttpsURLConnection) presignedUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);

            LOG.debug("Send msg {}", body);

            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            out.write(body);
            out.close();

            // Check the HTTP response code. To complete the upload and make the object available, 
            // you must interact with the connection object in some way.
            connection.getResponseCode();

            LOG.debug("HTTP response code: {}", connection.getResponseCode());

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            LOG.debug("response: {}", response.toString());

        } catch (Exception e) {
            throw new NjamsSdkRuntimeException("Error sending message", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String send(final String body, final Properties properties) {
        HttpsURLConnection connection = null;

        try {
            //Create connection
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("x-api-key", apikey);

            connection.setRequestProperty("Content-Length", Integer.toString(body.getBytes("UTF-8").length));
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
}
