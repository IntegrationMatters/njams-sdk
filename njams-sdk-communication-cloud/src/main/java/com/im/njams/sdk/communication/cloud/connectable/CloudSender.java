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
package com.im.njams.sdk.communication.cloud.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.cloud.CloudConstants;
import com.im.njams.sdk.communication.cloud.connector.sender.CloudSenderConnector;

import com.im.njams.sdk.communication.connectable.sender.AbstractSender;
import com.im.njams.sdk.communication.connectable.sender.Sender;
import com.im.njams.sdk.communication.connector.Connector;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.nio.charset.Charset.defaultCharset;

/**
 * @author stkniep
 */
public class CloudSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CloudSender.class);

    @Override
    public String getName() {
        return CloudConstants.COMMUNICATION_NAME;
    }

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#ENDPOINT}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#APIKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_INSTANCEID}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_CERTIFICATE}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#CLIENT_PRIVATEKEY}
     * <li>{@value com.im.njams.sdk.communication.cloud.CloudConstants#MAX_PAYLOAD_BYTES}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    protected Connector initialize(Properties properties) {
        return new CloudSenderConnector(properties, getName() + Connector.SENDER_NAME_ENDING);
    }

    protected void fillProperties(Properties properties, CommonMessage msg, String eventType) {
        properties.put(Sender.NJAMS_CLOUD_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_CLOUD_PATH, msg.getPath());
        properties.put(Sender.NJAMS_CLOUD_MESSAGETYPE, eventType);
        if (eventType.equals(Sender.NJAMS_MESSAGETYPE_EVENT)) {
            properties.put(Sender.NJAMS_CLOUD_LOGID, ((LogMessage) msg).getLogId());
        }
    }

    @Override
    protected void send(final LogMessage msg) {
        final Properties properties = new Properties();
        String eventType = Sender.NJAMS_MESSAGETYPE_EVENT;
        fillProperties(properties, msg, eventType);
        try {
            LOG.debug("Sending log message");
            final String response = send(msg, properties);
            LOG.debug("Response: " + response);
        } catch (final IOException ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    protected void send(final ProjectMessage msg) {
        final Properties properties = new Properties();
        String eventType = Sender.NJAMS_MESSAGETYPE_PROJECT;
        fillProperties(properties, msg, eventType);

        try {
            LOG.debug("Sending project message");
            final String response = send(msg, properties);
            LOG.debug(response);
        } catch (final IOException ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    protected void send(TraceMessage msg) throws NjamsSdkRuntimeException {
        final Properties properties = new Properties();
        String eventType = Sender.NJAMS_MESSAGETYPE_TRACE;
        fillProperties(properties, msg, eventType);

        try {
            LOG.debug("Sending TraceMessage");
            final String response = send(msg, properties);
            LOG.debug(response);
        } catch (final IOException ex) {
            LOG.error("Error sending TraceMessage", ex);
        }
    }

    protected String send(final Object msg, Properties properties) throws IOException {
        HttpURLConnection connection = null;
        try {
            //Create Connection
            String body = util.writeJson(msg);

            byte[] byteBody = body.getBytes("UTF-8");
            int utf8Bytes = byteBody.length;
            LOG.debug("Message size in Bytes: {}", utf8Bytes);

            URL url = ((CloudSenderConnector)connector).getUrl(utf8Bytes);
            boolean isPresignedUrl = !url.equals(((CloudSenderConnector)connector).getDefaultUrl());
            connection = ((CloudSenderConnector) connector).getConnection(url, isPresignedUrl);
            addAddionalProperties(connection, properties);

            LOG.debug("Send msg {}", body);
            return sendMessage(connection, body, isPresignedUrl);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String sendMessage(HttpURLConnection connection, String body, boolean isPresignedUrl) throws IOException {
        //Send request
        if(!isPresignedUrl) {
            connection.setRequestProperty("Content-Length", Integer.toString(body.getBytes("UTF-8").length));
        }

        try (final OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream())) {
            out.write(body);
        }

        // Check the HTTP response code. To complete the upload and make the object available,
        // you must interact with the connection object in some way.
        final int responseCode = connection.getResponseCode();

        LOG.debug("HTTP response code: {}", connection.getResponseCode());

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
        final String toString = new StringBuilder("rc = ")
                .append(responseCode)
                .append(", logId=")
                .append('"')
                .append(response)
                .append('"')
                .toString();
        return toString;
    }

    protected void addAddionalProperties(final HttpURLConnection connection, Properties properties) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(
                entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }
}
