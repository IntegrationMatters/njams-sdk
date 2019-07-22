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
package com.im.njams.sdk.communication_to_merge.cloud.connector.sender;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_to_merge.cloud.CloudConstants;
import com.im.njams.sdk.communication_to_merge.cloud.connector.Endpoints;
import com.im.njams.sdk.communication_to_merge.cloud.connector.CloudConnector;
import com.im.njams.sdk.communication_to_merge.connectable.sender.Sender;
import com.im.njams.sdk.utils.JsonUtils;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Todo: write doc
 */
public class CloudSenderConnector extends CloudConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudSenderConnector.class);

    /**
     * Todo: write doc
     */
    public static final int FALLBACK_MAX_PAYLOAD_BYTES = 10485760;

    private int maxPayloadBytes;

    protected URL defaultUrl;

    public CloudSenderConnector(Properties properties, String name) {
        super(properties, name);

        try {
            maxPayloadBytes = Integer.parseInt(properties.getProperty(CloudConstants.MAX_PAYLOAD_BYTES));
            LOG.debug("maxPayloadBytes: {} Bytes", maxPayloadBytes);
        } catch (Exception e) {
            LOG.warn("Invalid value for maxPayloadBytes, fallback to {} Bytes", FALLBACK_MAX_PAYLOAD_BYTES);
            maxPayloadBytes = FALLBACK_MAX_PAYLOAD_BYTES;
        }
        if (maxPayloadBytes > FALLBACK_MAX_PAYLOAD_BYTES) {
            LOG.warn("The value for maxPayloadBytes: {} is too great, fallback to {} Bytes", maxPayloadBytes, FALLBACK_MAX_PAYLOAD_BYTES);
            maxPayloadBytes = FALLBACK_MAX_PAYLOAD_BYTES;
        }

        setDefaultUrl(properties);
    }

    @Override
    protected List<Exception> extClose() {
        List<Exception> exceptions = new ArrayList<>();
        return exceptions;
    }

    public final URL getDefaultUrl(){
        return defaultUrl;
    }

    protected void loadKeystore() throws IOException{
        //TODO: delete/comment this method for production instead of using the apikey
        //See HttpsSenderConnector loadKeyStore()
    }

    public final HttpURLConnection getConnection(URL url, boolean isPresignedUrl) throws IOException{
        if(!isPresignedUrl){
            return getConnection(url);
        }else{
            return getPresignedConnection(url);
        }
    }

    public HttpURLConnection getConnection(URL url) throws IOException{
            //Create connection
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            setRequestProperties(connection);

            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("x-api-key", apikey);

            connection.getRequestProperties().entrySet().forEach(e -> LOG.debug("Header {} : {}", e.getKey(), e.getValue()));

            return connection;
    }

    protected void setRequestProperties(HttpURLConnection connection) throws ProtocolException {
        connection.setRequestMethod(CloudConstants.HTTP_REQUEST_TYPE_POST);
        connection.setRequestProperty(CloudConstants.CONTENT_TYPE, CloudConstants.CONTENT_TYPE_JSON + ", " + CloudConstants.UTF_8);
        connection.setRequestProperty(CloudConstants.ACCEPT, CloudConstants.CONTENT_TYPE_TEXT);
        connection.setRequestProperty(CloudConstants.CONTENT_LANGUAGE, CloudConstants.CONTENT_LANGUAGE_EN_US);

        connection.setUseCaches(false);
        connection.setDoOutput(true);
    }

    protected HttpURLConnection getPresignedConnection(URL url) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        return connection;
    }

    public URL getPresignedUrl(final URL defaultUrl) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) defaultUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("x-api-key", apikey);
        connection.setRequestProperty(Sender.NJAMS_CLOUD_MESSAGEVERSION, MessageVersion.V4.toString());

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

    protected void setDefaultUrl(Properties properties) {
        try {
            defaultUrl = new URL(getIngestEndpoint(properties.getProperty(CloudConstants.ENDPOINT)));
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init cloud sender", ex);
        }
    }

    public String getIngestEndpoint(String endpoint) throws IOException {
        Endpoints endpoints = super.getEndpoints(endpoint);
        return endpoints.ingest.startsWith("https://") ? endpoints.ingest : "https://" + endpoints.ingest;
    }

    public URL getUrl(int utf8Bytes) throws IOException {
        if (utf8Bytes > maxPayloadBytes) {
            LOG.debug("Message exceeds Byte limit: {}/{}", utf8Bytes, maxPayloadBytes);
            return getPresignedUrl(defaultUrl);
        } else {
            return defaultUrl;
        }
    }

    @Override
    public void connect() {
        //Do nothing, a connection has to be established with each send.
    }
}
