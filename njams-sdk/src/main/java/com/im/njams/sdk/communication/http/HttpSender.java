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
import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_CONNECTION_TEST;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_PREFIX;
import static com.im.njams.sdk.NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.function.Supplier;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
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
    private static class ConnectionTest {
        private final URI uri;
        private final Supplier<Response> request;
        private final String method;

        private ConnectionTest(URI uri, Supplier<Response> request, String method) {
            this.uri = uri;
            this.request = request;
            this.method = method;
        }

        private Response execute() {
            Response response = request.get();
            if (LOG.isDebugEnabled()) {
                final StatusType status = response.getStatusInfo();
                LOG.debug("{} response={} [{}]", this, status.getStatusCode(), status.getReasonPhrase());
            }
            return response;
        }

        @Override
        public String toString() {
            return method + " " + uri;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);

    /**
     * Name of the HTTP Header for message version
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
     * Name if the HTTP Header for message type
     */
    private static final String NJAMS_MESSAGETYPE_HTTP_HEADER = "njams-messagetype";

    protected final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
    /**
     * Name of the HTTP sender
     */
    public static final String NAME = "HTTP";

    /**
     * this is the API path to the nJAMS ingest service
     */
    private static final String INGEST_API_PATH = "api/processing/ingest/";
    private static final String LEGACY_CONNECTION_TEST_PATH = "api/public/version";
    private static final int EXCEPTION_IDLE_TIME = 50;
    private static final int MAX_TRIES = 100;

    protected URI uri;
    protected Client client;
    protected WebTarget target;

    private enum ConnectionTestMode {
        /** Initial value that triggers trying {@link #STANDARD} first, then falling back to {@link #LEGACY} */
        DETECT,
        /** Standard connection test using <code>HEAD</code> on ingest resource (since nJAMS 5.3.4) */
        STANDARD,
        /** Fallback connection test just checking nJAMS' public API */
        LEGACY
    }

    private static ConnectionTestMode connectionTestMode = ConnectionTestMode.DETECT;

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
    @SuppressWarnings("removal")
    @Override
    public void init(Properties properties) {
        this.properties = properties;
        try {
            final String suffix =
                    Settings.getPropertyWithDeprecationWarning(properties, PROPERTY_HTTP_DATAPROVIDER_SUFFIX,
                            PROPERTY_HTTP_DATAPROVIDER_PREFIX);
            if (StringUtils.isBlank(suffix)) {
                throw new NjamsSdkRuntimeException(
                        "Required parameter " + PROPERTY_HTTP_DATAPROVIDER_SUFFIX + " is missing.");
            }
            uri = createUri(INGEST_API_PATH + suffix);
            final boolean legacy = "legacy".equalsIgnoreCase(properties.getProperty(PROPERTY_HTTP_CONNECTION_TEST));
            if (legacy) {
                synchronized (HttpSender.class) {
                    connectionTestMode = ConnectionTestMode.LEGACY;
                }
            }
            connect();
            LOG.debug("Initialized http sender with url {}", uri);
        } catch (final Exception e) {
            LOG.debug("Could not initialize sender with URL {}", uri, e);
        }
    }

    private URI createUri(String path) throws URISyntaxException {
        final String base = createBaseUri();
        return new URI(base + path);
    }

    private String createBaseUri() {
        String base = properties.getProperty(PROPERTY_HTTP_BASE_URL);
        if (StringUtils.isBlank(base)) {
            throw new NjamsSdkRuntimeException("Required parameter " + PROPERTY_HTTP_BASE_URL + " is missing.");
        }
        if (base.charAt(base.length() - 1) != '/') {
            base += "/";
        }
        return base;
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
            target = client.target(uri);
            testConnection();
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (final Exception e) {
            close();
            if (e instanceof NjamsSdkRuntimeException) {
                throw e;
            }
            throw new NjamsSdkRuntimeException("Failed to connect", e);
        }
    }

    protected void testConnection() {
        if (target == null) {
            throw new NullPointerException("No target");
        }
        final ConnectionTest connectionTest = getConnectionTest();
        final Response response = connectionTest.execute();
        if (response.getStatus() == Status.OK.getStatusCode()) {
            return;
        }
        if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
            throw new IllegalStateException("No active dataprovider found at: " + connectionTest.uri);
        }
        final StatusType status = response.getStatusInfo();
        throw new IllegalStateException("Received unexpected status " + status.getStatusCode() + " ("
                + status.getReasonPhrase() + ") from: " + connectionTest);
    }

    private ConnectionTest getConnectionTest() {
        if (connectionTestMode == ConnectionTestMode.DETECT) {
            synchronized (HttpSender.class) {
                if (connectionTestMode == ConnectionTestMode.DETECT) {
                    final ConnectionTest connectionTest = getConnectionTest(ConnectionTestMode.STANDARD);
                    final Response response = connectionTest.execute();
                    if (response.getStatus() == Status.METHOD_NOT_ALLOWED.getStatusCode()) {
                        // 405 -> The server does not know about HEAD on that resource -> use legacy fallback
                        connectionTestMode = ConnectionTestMode.LEGACY;
                        LOG.info("Switched to legacy http connection test implementation.");
                        return getConnectionTest(ConnectionTestMode.LEGACY);
                    }
                    connectionTestMode = ConnectionTestMode.STANDARD;
                    return connectionTest;
                }
            }
        }
        return getConnectionTest(connectionTestMode);
    }

    private ConnectionTest getConnectionTest(ConnectionTestMode mode) {
        if (mode == ConnectionTestMode.LEGACY) {
            // Fallback for old nJAMS not implementing HEAD; however, this does not tell whether a DP is 
            // listening on the requested path suffix, but it confirms that a server is listening on that address
            try {
                final URI uri = createUri(LEGACY_CONNECTION_TEST_PATH);
                return new ConnectionTest(uri, () -> client.target(uri).request().get(), "GET");
            } catch (URISyntaxException e) {
                // actually, this cannot happen since creating the actual ingest URI would have failed before
                throw new IllegalArgumentException(e);
            }
        }
        return new ConnectionTest(uri, () -> target.request().head(), "HEAD");

    }

    /**
     * Close the HTTP Client
     */
    @Override
    public void close() {
        LOG.debug("Called close on HTTP Sender.");
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
        final Properties properties = createProperties(msg, clientSessionId);
        properties.put(Sender.NJAMS_LOGID, msg.getLogId());
        try {
            LOG.trace("Sending log message {}", msg.getLogId());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send log message", ex);
        }
    }

    @Override
    protected void send(final ProjectMessage msg, String clientSessionId) {
        final Properties properties = createProperties(msg, clientSessionId);
        try {
            LOG.trace("Sending project message for {}", msg.getPath());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send project message", ex);
        }
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        final Properties properties = createProperties(msg, clientSessionId);
        try {
            LOG.trace("Sending TraceMessage for {}", msg.getPath());
            tryToSend(msg, properties);
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send trace message", ex);
        }
    }

    private Properties createProperties(CommonMessage msg, String clientSessionId) {
        final Properties properties = new Properties();
        properties.put(Sender.NJAMS_MESSAGEVERSION, MessageVersion.V4.toString());
        properties.put(Sender.NJAMS_PATH, msg.getPath());
        properties.put(Sender.NJAMS_CLIENTID, clientSessionId);
        final String msgType;
        if (msg instanceof LogMessage) {
            msgType = Sender.NJAMS_MESSAGETYPE_EVENT;
        } else if (msg instanceof ProjectMessage) {
            msgType = Sender.NJAMS_MESSAGETYPE_PROJECT;
        } else if (msg instanceof TraceMessage) {
            msgType = Sender.NJAMS_MESSAGETYPE_TRACE;
        } else {
            throw new IllegalArgumentException("Unknown message type: " + msg.getClass());
        }
        properties.put(Sender.NJAMS_MESSAGETYPE, msgType);
        return properties;
    }

    private void tryToSend(final Object msg, final Properties properties) throws InterruptedException {
        boolean sent = false;
        int responseStatus = -1;
        int tries = 0;
        Exception exception = null;
        final String json = JsonUtils.serialize(msg);
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
                    LOG.warn("Start reconnect because the server HTTP endpoint could not be reached for {} seconds.",
                            MAX_TRIES * EXCEPTION_IDLE_TIME / 1000);
                    if (exception != null) {
                        throw new NjamsSdkRuntimeException("Error sending message with HTTP client URI "
                                + target.getUri(), exception);
                    }
                    throw new NjamsSdkRuntimeException("Error sending message with HTTP client URI "
                            + target.getUri() + " Response status is: " + responseStatus);
                }
                Thread.sleep(EXCEPTION_IDLE_TIME);
            }
            exception = null;
            responseStatus = -1;

        } while (!sent);
    }

    private int send(final String msg, final Properties properties) {
        final Response response = target.request()
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
        if (LOG.isTraceEnabled()) {
            LOG.trace("POST response: {} [{}]", response.getStatus(), response.getStatusInfo().getReasonPhrase());
        }
        return response.getStatus();
    }

    @Override
    public String getName() {
        return NAME;
    }

}
