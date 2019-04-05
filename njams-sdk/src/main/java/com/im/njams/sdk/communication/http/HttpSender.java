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
package com.im.njams.sdk.communication.http;

import static java.nio.charset.Charset.defaultCharset;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.Settings;

/**
 * HttpSender
 *
 * @author stkniep
 */
public class HttpSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpSender.class);

    private static final String PROPERTY_PREFIX = "njams.sdk.communication.http";
    protected ObjectMapper mapper;
    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTP";

    /**
     * http sender urlport
     */
    public static final String SENDER_URL = PROPERTY_PREFIX + ".sender.urlport";
    /**
     * http sender username
     */
    public static final String SENDER_USERNAME = PROPERTY_PREFIX + ".sender.username";
    /**
     * http sender password
     */
    public static final String SENDER_PASSWORD = PROPERTY_PREFIX + ".sender.password";
    private String user;
    private String password;
    private URL url;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.http.HttpSender#SENDER_URL}
     * <li>{@value com.im.njams.sdk.communication.http.HttpSender#SENDER_USERNAME}
     * <li>{@value com.im.njams.sdk.communication.http.HttpSender#SENDER_PASSWORD}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        this.properties = properties;
        discardPolicy = properties.getProperty(Settings.PROPERTY_DISCARD_POLICY, "none").toLowerCase();
        try {
            url = new URL(properties.getProperty(SENDER_URL));
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
        user = properties.getProperty(SENDER_USERNAME);
        password = properties.getProperty(SENDER_PASSWORD);
    }

    @Override
    protected void send(final LogMessage msg) {
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
        properties.put(Sender.NJAMS_PATH, msg.getPath());
        properties.put(Sender.NJAMS_LOGID, msg.getLogId());
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
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
        properties.put(Sender.NJAMS_PATH, msg.getPath());

        try {
            LOG.debug("Sending project message");
            final String response = send(msg, properties);
            LOG.debug(response);
        } catch (final IOException ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    protected void send(TraceMessage msg) throws NjamsSdkRuntimeException{
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_TRACE);
        properties.put(Sender.NJAMS_PATH, msg.getPath());

        try {
            LOG.debug("Sending TraceMessage");
            final String response = send(msg, properties);
            LOG.debug(response);
        } catch (final IOException ex) {
            LOG.error("Error sending TraceMessage", ex);
        }
    }

    private void addAddtionalProperties(final Properties properties, final HttpURLConnection connection) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(
                entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }

    private String send(final Object msg, final Properties properties) throws IOException {
        HttpURLConnection connection = null;

        try {
            //Create connection
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/plain");

            if (user != null) {
                final Base64.Encoder encoder = Base64.getEncoder();
                final String userpassword = user + ":" + password;
                final byte[] encodedAuthorization = encoder.encode(userpassword.getBytes(defaultCharset()));
                connection.setRequestProperty("Authorization",
                        "Basic " + new String(encodedAuthorization, defaultCharset()));
            }

            final String body = mapper.writeValueAsString(msg);
            connection.setRequestProperty("Content-Length",
                    Integer.toString(body.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            connection.setRequestProperty(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
            addAddtionalProperties(properties, connection);

            //Send request
            try (final DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(body);
            }

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
            final String toString = new StringBuilder("rc = ")
                    .append(responseCode)
                    .append(", logId=")
                    .append('"')
                    .append(response)
                    .append('"')
                    .toString();
            return toString;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

}
