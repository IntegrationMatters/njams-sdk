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
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.utils.JsonUtils;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import static java.nio.charset.Charset.defaultCharset;
import java.security.KeyStore;
import java.util.Base64;
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
    public static final String NJAMS_ORGMESSAGETYPE = "x-njams-orgmessagetype";
    public static final String NJAMS_CHUNK = "x-njams-chunk";
    public static final String NJAMS_MESSAGEVERSION = "x-njams-messageversion";
    public static final String NJAMS_PATH = "x-njams-path";
    public static final String NJAMS_LOGID = "x-njams-logid";

    public static final int MAX_PAYLOAD_BYTES = 900000;

    private URL url;
    private String apikey;
    private String instanceId;

    private boolean endpointError = false;
    private String endpointErrorMessage = "";
    private long reconnectTime = 0;

    private String endpointUrl;
    private String connectionId;

    @Override
    public void init(Properties properties) {

        String apikeypath = properties.getProperty(CloudConstants.APIKEY);

        if (apikeypath == null) {
            LOG.error("Please provide property {} for CloudSender", CloudConstants.APIKEY);
        }

        String instanceIdPath = properties.getProperty(CloudConstants.CLIENT_INSTANCEID);

        if (instanceIdPath == null) {
            LOG.error("Please provide property {} for CloudSender", CloudConstants.CLIENT_INSTANCEID);
        }

        try {
            apikey = FileStringReader.getStringFromFile(apikeypath);
        } catch (Exception e) {
            LOG.error("Failed to load api key from file " + apikeypath, e);
            throw new IllegalStateException("Failed to load api key from file");
        }

        try {
            instanceId = FileStringReader.getStringFromFile(instanceIdPath);
        } catch (Exception e) {
            LOG.error("Failed to load instanceId from file " + instanceIdPath, e);
            throw new IllegalStateException("Failed to load instanceId from file");
        }
        
        String endpointUrlPath = properties.getProperty(CloudConstants.ENDPOINT);

        try {
        	 endpointUrl = FileStringReader.getStringFromFile(endpointUrlPath);
        } catch (Exception e) {
            LOG.error("Failed to load endpoint from file " + endpointUrlPath, e);
            throw new IllegalStateException("Failed to load endpoint from file");
        }      

        // build connectionId String
        CloudClientId cloudClientId = CloudClientId.getInstance(instanceId, false);
        // sender always uses suffix = 0
        connectionId = cloudClientId.clientId + "_0";
        LOG.debug("connectionId: {}", connectionId);

        try {
            url = new URL(endpointUrl);
            this.connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception ex) {
            this.connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("unable to init CloudSender", ex);
        }
    }

    @Override
    public String getName() {
        return CloudConstants.NAME;
    }

    protected void retryInit() {
        try {
            url = new URL(endpointUrl);
            this.connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception ex) {
            this.connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("unable to init CloudSender", ex);
        }
    }

    protected void sendMessage(final CommonMessage msg, final Properties properties) {
        if (!endpointError) {
            try {
                LOG.trace("Sending {} message", properties.get(NJAMS_MESSAGETYPE));
                final String body = JsonUtils.serialize(msg);

                LOG.trace("Send msg {}", body);

                int len = body.length();
                LOG.debug("Message size: {}", len);
                if (len > MAX_PAYLOAD_BYTES) {
                    LOG.debug("Message exceeds size limit: {}/{}", len, MAX_PAYLOAD_BYTES);

                    String bodyEncoded = Base64.getEncoder().encodeToString(body.getBytes("utf-8"));
                    len = bodyEncoded.length();

                    LOG.debug("Encoded message size: {}", len);

                    int chunkMax = (int) Math.ceil((double) len / MAX_PAYLOAD_BYTES);
                    chunkMax = chunkMax - 1;

                    LOG.debug("chunkMax: {}", chunkMax);
                    int chunkCounter = 0;

                    LOG.debug("len: {}", len);

                    properties.setProperty(NJAMS_ORGMESSAGETYPE, properties.getProperty(NJAMS_MESSAGETYPE));
                    properties.setProperty(NJAMS_MESSAGETYPE, "chunked");

                    for (int i = 0; i < len; i += MAX_PAYLOAD_BYTES) {
                        String chunk = bodyEncoded.substring(i, Math.min(len, i + MAX_PAYLOAD_BYTES));
                        LOG.debug("CHUNK {}/{}\n {}", chunkCounter, chunkMax);

                        properties.setProperty(NJAMS_CHUNK, chunkCounter + ";" + chunkMax);
                        send(chunk, properties);
                        chunkCounter++;
                    }

                } else {
                    final Response response = send(body, properties);
                    LOG.debug("Response statusCode: {} body: {} ", response.getStatusCode(), response.getBody());

                    if (response.getStatusCode() != 200) {
                        switch (response.getStatusCode()) {
                        case (429):
                            LOG.warn(
                                    "Message ({}) discarded since the endpoint returns that too many requests are made (HTTP 429 Too Many Requests). This is caused by you crossing your booked tier.",
                                    properties.get(NJAMS_MESSAGETYPE));
                            break;
                        case (500):
                            LOG.error("Error sending {} please contact support referencing: {} {} ",
                                    properties.get(NJAMS_MESSAGETYPE), instanceId, response.getBody());
                        }
                    }

                }

            } catch (Exception ex) {
                LOG.error("Error sending {}", properties.get(NJAMS_MESSAGETYPE), ex);
            }
        } else {
            LOG.warn(
                    "Message ({}) discarded because your client is not allowed to connect the CloudSender due to the following problem: {}",
                    properties.get(NJAMS_MESSAGETYPE), endpointErrorMessage);
            if (System.currentTimeMillis() > reconnectTime) {
                LOG.info("Try to establish connection again.");
                retryInit();
            }
        }
    }

    @Override
    protected void send(final LogMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
        properties.put(NJAMS_PATH, msg.getPath());
        properties.put(NJAMS_LOGID, msg.getLogId());

        sendMessage(msg, properties);

    }

    @Override
    protected void send(final ProjectMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
        properties.put(NJAMS_PATH, msg.getPath());

        sendMessage(msg, properties);
    }

    @Override
    protected void send(final TraceMessage msg) {
        final Properties properties = new Properties();
        properties.put(NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_TRACE);
        properties.put(NJAMS_PATH, msg.getPath());

        sendMessage(msg, properties);
    }


    private void addAddtionalProperties(final Properties properties, final HttpsURLConnection connection) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(
                entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }

    private Response send(String body, final Properties properties) {
        HttpsURLConnection connection = null;

        try {
            byte[] bodyCompressed = Gzip.compress(body);

            // Create connection
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("x-api-key", apikey);
            connection.setRequestProperty("x-instance-id", instanceId);

            connection.setRequestProperty("Content-Length", Integer.toString(bodyCompressed.length));
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setRequestProperty("Content-Encoding", "gzip");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            connection.setRequestProperty(NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
            addAddtionalProperties(properties, connection);

            connection.getRequestProperties().entrySet()
                    .forEach(e -> LOG.debug("Header {} : {}", e.getKey(), e.getValue()));

            // Send request
            try (final DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(bodyCompressed);
            }

            final int responseCode = connection.getResponseCode();

            if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {

                // Get Response
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
                return new Response(responseCode, response.toString());

            } else {
                // Get Error Response
                final InputStream is = connection.getErrorStream();
                final StringBuilder response;
                try (final BufferedReader rd = new BufferedReader(new InputStreamReader(is, defaultCharset()))) {
                    response = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }
                }

                return new Response(responseCode, response.toString());

            }
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
            try (InputStream keystoreInput = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("client.ks");
                    InputStream truststoreInput = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream("client.ts")) {
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
            final TrustManagerFactory trustFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
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