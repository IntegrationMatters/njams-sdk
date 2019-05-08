package com.im.njams.sdk.communication.cloud.connector;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.cloud.ApiKeyReader;
import com.im.njams.sdk.communication.cloud.CloudConstants;
import com.im.njams.sdk.communication.cloud.Endpoints;
import com.im.njams.sdk.communication.cloud.PresignedUrl;
import com.im.njams.sdk.communication.connectable.Sender;
import com.im.njams.sdk.communication.http.https.connector.HttpsSenderConnector;
import com.im.njams.sdk.utils.JsonUtils;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class CloudSenderConnector extends HttpsSenderConnector {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudSenderConnector.class);

    public static final int FALLBACK_MAX_PAYLOAD_BYTES = 10485760;

    private String apikey;

    private int maxPayloadBytes;

    public CloudSenderConnector(Properties properties, String name) {
        super(properties, name);

        String apikeypath = properties.getProperty(CloudConstants.APIKEY);

        if (apikeypath == null) {
            LOG.error("Please provide property {} for CloudSender", CloudConstants.APIKEY);
        }
        try {
            apikey = ApiKeyReader.getApiKey(apikeypath);
        } catch (Exception e) {
            LOG.error("Failed to load api key from file " + apikeypath, e);
            throw new IllegalStateException("Failed to load api key from file");
        }

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
    }

    @Override
    protected void loadKeystore() throws IOException{
        //TODO: delete/comment this method for production instead of using the apikey
    }

    @Override
    public HttpURLConnection getConnection(URL url) throws IOException{
            //Create connection
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            super.setDefaultRequestProperties(connection);

            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("x-api-key", apikey);

            connection.getRequestProperties().entrySet().forEach(e -> LOG.debug("Header {} : {}", e.getKey(), e.getValue()));

            return connection;
    }

    @Override
    protected HttpURLConnection getPresignedConnection(URL url) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        return connection;
    }

    /**
     * @return a presignedUrl
     */
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

    @Override
    protected void setDefaultUrl(Properties properties) {
        try {
            defaultUrl = new URL(getIngestEndpoint(properties.getProperty(CloudConstants.ENDPOINT)));
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init cloud sender", ex);
        }
    }

    /**
     * @return the ingest endpoint
     */
    public String getIngestEndpoint(String endpoint) throws Exception {
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

    @Override
    public URL getUrl(int utf8Bytes, Properties properties) throws IOException {
        if (utf8Bytes > maxPayloadBytes) {
            LOG.debug("Message exceeds Byte limit: {}/{}", utf8Bytes, maxPayloadBytes);
            return getPresignedUrl(defaultUrl);
        } else {
            return defaultUrl;
        }
    }
}
