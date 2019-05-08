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
package com.im.njams.sdk.communication.http.connectable;

import static java.nio.charset.Charset.defaultCharset;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.communication.connector.AbstractConnector;
import com.im.njams.sdk.communication.connector.Connector;
import com.im.njams.sdk.communication.http.HttpConstants;
import com.im.njams.sdk.communication.http.connector.HttpSenderConnector;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connectable.AbstractSender;
import com.im.njams.sdk.communication.connectable.Sender;

/**
 * HttpSender
 *
 * @author stkniep
 */
public class HttpSender extends AbstractSender {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HttpSender.class);


    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_URL}
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_USERNAME}
     * <li>{@value com.im.njams.sdk.communication.http.HttpConstants#SENDER_PASSWORD}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    protected Connector initialize(Properties properties) {
        return new HttpSenderConnector(properties, getName() + Connector.SENDER_NAME_ENDING);
    }

    @Override
    protected void send(final LogMessage msg) {
        final Properties properties = new Properties();
        setCommonProperties(properties, msg);
        properties.put(NJAMS_MESSAGETYPE, NJAMS_MESSAGETYPE_EVENT);
        properties.put(NJAMS_LOGID, msg.getLogId());
        try {
            LOG.debug("Sending log message");
            final String response = send(msg, properties);
            LOG.debug("Response: " + response);
        } catch (final IOException ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    protected void setCommonProperties(Properties properties, CommonMessage msg){
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_PATH, msg.getPath());
    }

    @Override
    protected void send(final ProjectMessage msg) {
        final Properties properties = new Properties();
        setCommonProperties(properties, msg);
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);

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
        setCommonProperties(properties, msg);
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_TRACE);

        try {
            LOG.debug("Sending TraceMessage");
            final String response = send(msg, properties);
            LOG.debug(response);
        } catch (final IOException ex) {
            LOG.error("Error sending TraceMessage", ex);
        }
    }

    @Override
    protected void extStop() {
        //Nothing to do
    }

    protected String send(final Object msg, Properties properties) throws IOException {
        HttpURLConnection connection = null;
        try {
            //Create Connection
            connection = ((HttpSenderConnector) connector).getConnection();
            String body = ((AbstractConnector)connector).getMapper().writeValueAsString(msg);
            connection.setRequestProperty("Content-Length",
                    Integer.toString(body.getBytes().length));
            addAddtionalProperties(connection, properties);

            return sendMessage(connection, body);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String sendMessage(HttpURLConnection connection, String body) throws IOException{
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
    }

    protected void addAddtionalProperties(final HttpURLConnection connection, Properties properties) {
        final Set<Map.Entry<Object, Object>> entrySet = properties.entrySet();
        entrySet.forEach(
                entry -> connection.setRequestProperty(entry.getKey().toString(), entry.getValue().toString()));
    }

    @Override
    public String getName() {
        return HttpConstants.COMMUNICATION_NAME_HTTP;
    }


}
