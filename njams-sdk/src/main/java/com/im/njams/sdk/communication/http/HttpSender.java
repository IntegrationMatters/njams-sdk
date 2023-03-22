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

import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_BASE_URL;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_PREFIX;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.DiscardMonitor;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Sends Messages via HTTP to nJAMS
 *
 * @author bwand
 */
public class HttpSender extends AbstractSender {
    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);

    /**
     * Name of the HTTP Header for Messageversion
     */
    private static final String NJAMS_MESSAGEVERSION_HTTP_HEADER = "njams-messageversion";
    /**
     * Name of the HTTP Header for Path
     */
    private static final String NJAMS_PATH_HTTP_HEADER = "njams-path";
    /**
     * Name of the HTTP Header for Logid
     */
    private static final String NJAMS_LOGID_HTTP_HEADER = "njams-logid";
    /**
     * Name of the HTTP Header for clientId
     */
    private static final String NJAMS_CLIENTID_HTTP_HEADER = "njams-clientid";
    /**
     * Name if the HTTP Header for Messagetype
     */
    private static final String NJAMS_MESSAGETYPE_HTTP_HEADER = "njams-messagetpe";

    protected final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
    /**
     * Name of the HTTP component
     */
    public static final String NAME = "HTTP";

    /**
     * this is the API path to the ingest
     */
    protected static final String INGEST_API_PATH = "api/processing/ingest/";
    private static final int EXCEPTION_IDLE_TIME = 50;
    private static final int MAX_TRIES = 100;

    protected URL url;
    protected Client client;
    protected WebTarget target;

    /**
     * Initializes this Sender via the given Properties.
     * <p>
     * Valid properties are:
     * <ul>
     * <li>{@value NjamsSettings#PROPERTY_HTTP_BASE_URL}
     * <li>{@value NjamsSettings#PROPERTY_HTTP_DATAPROVIDER_PREFIX}
     * </ul>
     *
     * @param properties the properties needed to initialize
     */
    @Override
    public void init(Properties properties) {
        this.properties = properties;
        try {
            url = createUrl(properties);
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
        try {
            connect();
            LOG.debug("Initialized http sender with url {}", url);
        } catch (final NjamsSdkRuntimeException e) {
            LOG.error("Could not initialize sender with url {}\n", url, e);
        }
    }

    private URL createUrl(Properties properties) throws MalformedURLException {
        String base = properties.getProperty(PROPERTY_HTTP_BASE_URL);
        if (StringUtils.isBlank(base)) {
            throw new NjamsSdkRuntimeException("Required parameter " + PROPERTY_HTTP_BASE_URL + " is missing.");
        }
        if (base.charAt(base.length() - 1) != '/') {
            base += "/";
        }
        final String suffix = Settings.getPropertyWithDeprecationWarning(properties, PROPERTY_HTTP_DATAPROVIDER_SUFFIX,
                PROPERTY_HTTP_DATAPROVIDER_PREFIX);
        if (StringUtils.isBlank(suffix)) {
            throw new NjamsSdkRuntimeException(
                    "Required parameter " + PROPERTY_HTTP_DATAPROVIDER_SUFFIX + " is missing.");
        }
        return new URL(base + INGEST_API_PATH + suffix);
    }

    /**
     * Create the HTTP Client for Events.
     */
    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            client = ClientBuilder.newClient();
            target = client.target(String.valueOf(url));
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            if (client != null) {
                client.close();
                client = null;
                target = null;
            }
            throw new NjamsSdkRuntimeException("Unable to connect", e);
        }
    }

    /**
     * Close the HTTP Client
     */
    @Override
    public void close() {
        LOG.info("Called close on HTTP Sender.");
        connectionStatus = ConnectionStatus.DISCONNECTED;
        if (client != null) {
            try {
                client.close();
                client = null;
                target = null;
            } catch (Exception ex) {
                LOG.error("Error closing HTTP connection.", ex);
            }
        }
    }

    @Override
    protected void send(final LogMessage msg, String clientSessionId) {
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_EVENT);
        properties.put(Sender.NJAMS_PATH, msg.getPath());
        properties.put(Sender.NJAMS_LOGID, msg.getLogId());
        properties.put(Sender.NJAMS_CLIENTID, clientSessionId);
        try {
            LOG.debug("Sending log message {}", msg.getLogId());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            LOG.error("Error sending LogMessage", ex);
        }
    }

    @Override
    protected void send(final ProjectMessage msg, String clientSessionId) {
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_PROJECT);
        properties.put(Sender.NJAMS_PATH, msg.getPath());
        properties.put(Sender.NJAMS_CLIENTID, clientSessionId);
        try {
            LOG.debug("Sending project message for {}", msg.getPath());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            LOG.error("Error sending ProjectMessage", ex);
        }
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_MESSAGETYPE, Sender.NJAMS_MESSAGETYPE_TRACE);
        properties.put(Sender.NJAMS_PATH, msg.getPath());
        properties.put(Sender.NJAMS_CLIENTID, clientSessionId);
        try {
            LOG.debug("Sending TraceMessage for {}", msg.getPath());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            LOG.error("Error sending TraceMessage", ex);
        }
    }

    private void tryToSend(final Object msg, final Properties properties) throws InterruptedException {
        boolean sent = false;
        int responseStatus = -1;
        int tries = 0;
        Exception exception = null;
        String json = JsonUtils.serialize(msg);
        do {
            try {
                responseStatus = send(json, properties);
                if (responseStatus == 200 || responseStatus == 204) {
                    sent = true;
                }
            } catch (Exception ex) {
                LOG.trace("Failed to send to {}:\n{}\nheaders={}", target.getUri(), json, properties, ex);
                exception = ex;
            }
            if (exception != null || !sent) {
                if (discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS) {
                    LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                    DiscardMonitor.discard();
                    break;
                }
                if (++tries >= MAX_TRIES) {
                    LOG.warn("Try to reconnect, because the Server HTTP Endpoint could not be reached for {} seconds.",
                            MAX_TRIES * EXCEPTION_IDLE_TIME / 1000);
                    if (exception != null) {
                        throw new NjamsSdkRuntimeException("Error sending Message with HTTP Client URI "
                                + target.getUri().toString(), exception);
                    }
                    throw new NjamsSdkRuntimeException("Error sending Message with HTTP Client URI "
                            + target.getUri().toString() + " Response Status is: " + responseStatus);
                }
                Thread.sleep(EXCEPTION_IDLE_TIME);
            }
            exception = null;
            responseStatus = -1;

        } while (!sent);
    }

    private int send(final String msg, final Properties properties) {
        Response response = target.request()
                .header("Content-Type", "application/json")
                .header("Accept", "text/plain")
                .header(NJAMS_MESSAGEVERSION_HTTP_HEADER, MessageVersion.V4.toString())
                .header(NJAMS_MESSAGETYPE_HTTP_HEADER, properties.getProperty(Sender.NJAMS_MESSAGETYPE))
                .header(NJAMS_PATH_HTTP_HEADER, properties.getProperty(Sender.NJAMS_PATH))
                .header(NJAMS_LOGID_HTTP_HEADER, properties.getProperty(Sender.NJAMS_LOGID))
                .header(NJAMS_CLIENTID_HTTP_HEADER, properties.getProperty(Sender.NJAMS_CLIENTID))

                // Additionally add old headers for old Server Versions < 5.3.0
                .header(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString())
                .header(Sender.NJAMS_MESSAGETYPE, properties.getProperty(Sender.NJAMS_MESSAGETYPE))
                .header(Sender.NJAMS_PATH, properties.getProperty(Sender.NJAMS_PATH))
                .header(Sender.NJAMS_LOGID, properties.getProperty(Sender.NJAMS_LOGID))
                .post(Entity.json(msg));
        LOG.debug("Response status:" + response.getStatus()
                + ", Message: " + response.getStatusInfo().getReasonPhrase());
        return response.getStatus();
    }

    @Override
    public String getName() {
        return NAME;
    }

}
