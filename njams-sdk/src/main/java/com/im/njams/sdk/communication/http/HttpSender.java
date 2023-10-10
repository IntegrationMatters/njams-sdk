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
import static com.im.njams.sdk.communication.MessageHeaders.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.communication.DiscardMonitor;
import com.im.njams.sdk.communication.DiscardPolicy;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends Messages via HTTP to nJAMS
 *
 * @author bwand
 */
public class HttpSender extends AbstractSender {
    @FunctionalInterface
    private interface TestCall {
        Response execute() throws IOException;
    }

    private static class ConnectionTest {
        private final URL url;
        private final TestCall request;
        private final String method;

        private ConnectionTest(URL url, TestCall request, String method) {
            this.url = url;
            this.request = request;
            this.method = method;
        }

        private Response execute() {
            try {
                final Response response = request.execute();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} response={}", this, response.code());
                }
                return response;
            } catch (IOException e) {
                LOG.debug("{} test call failed", this, e);
                return null;
            }
        }

        @Override
        public String toString() {
            return "ConnectionTest[" + method + " " + url + "]";
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);

    /**
     * Name of the HTTP(s) communication implementation.
     */
    public static final String NAME = "HTTP";

    /**
     * this is the API path to the nJAMS ingest service
     */
    private static final String INGEST_API_PATH = "api/processing/ingest/";
    private static final String LEGACY_CONNECTION_TEST_PATH = "api/public/version";
    private static final int EXCEPTION_IDLE_TIME = 50;
    private static final int MAX_TRIES = 100;

    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int OK = 200;

    protected URL url;
    protected OkHttpClient client;
    protected HttpClientFactory clientFactory;

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
            final URI uri = createUri(INGEST_API_PATH + suffix);
            url = uri.toURL();
            final boolean legacy = "legacy".equalsIgnoreCase(properties.getProperty(PROPERTY_HTTP_CONNECTION_TEST));
            if (legacy) {
                synchronized (HttpSender.class) {
                    connectionTestMode = ConnectionTestMode.LEGACY;
                }
            }
            clientFactory = new HttpClientFactory(properties, uri);
            connect();
            LOG.debug("Initialized sender with URL {}", uri);
        } catch (final Exception e) {
            LOG.debug("Could not initialize sender with URL {}", url, e);
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
            client = clientFactory.createClient();
            //            target = client.target(uri);
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

    private void testConnection() {
        final ConnectionTest connectionTest = getConnectionTest();
        final Response response = connectionTest.execute();
        if (response != null) {
            if (response.code() == OK) {
                return;
            }
            if (response.code() == NOT_FOUND) {
                throw new IllegalStateException("No active dataprovider found at: " + connectionTest.url);
            }
            throw new IllegalStateException(
                "Received unexpected status " + response.code() + " from: " + connectionTest);
        }
        throw new IllegalStateException(
            "No response from test call: " + connectionTest);

    }

    private ConnectionTest getConnectionTest() {
        if (connectionTestMode == ConnectionTestMode.DETECT) {
            synchronized (HttpSender.class) {
                if (connectionTestMode == ConnectionTestMode.DETECT) {
                    final ConnectionTest connectionTest = getConnectionTest(ConnectionTestMode.STANDARD);
                    final Response response = connectionTest.execute();
                    if (response.code() == METHOD_NOT_ALLOWED) {
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
                return new ConnectionTest(uri.toURL(),
                    () -> client.newCall(
                        new Request.Builder().addHeader("Content-Length", "0").url(uri.toURL()).get().build())
                        .execute(),
                    "GET");
            } catch (URISyntaxException | MalformedURLException e) {
                // actually, this cannot happen since creating the actual ingest URI would have failed before
                throw new IllegalArgumentException(e);
            }
        }
        return new ConnectionTest(url,
            () -> client.newCall(new Request.Builder().addHeader("Content-Length", "0").url(url).head().build())
                .execute(),
            "HEAD");

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
                client = null;
            } catch (Exception ex) {
                LOG.error("Error closing HTTP connection.", ex);
            }
        }
    }

    @Override
    protected void send(final LogMessage msg, String clientSessionId) {
        try {
            LOG.trace("Sending log message {}", msg.getLogId());
            tryToSend(msg, createHeaders(msg, clientSessionId));
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send log message", ex);
        }
    }

    @Override
    protected void send(final ProjectMessage msg, String clientSessionId) {
        try {
            LOG.trace("Sending project message for {}", msg.getPath());
            tryToSend(msg, createHeaders(msg, clientSessionId));
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send project message", ex);
        }
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) throws NjamsSdkRuntimeException {
        try {
            LOG.trace("Sending TraceMessage for {}", msg.getPath());
            tryToSend(msg, createHeaders(msg, clientSessionId));
        } catch (final Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new NjamsSdkRuntimeException("Failed to send trace message", ex);
        }
    }

    /**
     * For compatibility reasons both the common nJAMS headers and the Nginx compatible headers are added here.
     * @param msg
     * @param clientSessionId
     * @return
     */
    private Map<String, String> createHeaders(CommonMessage msg, String clientSessionId) {
        final Map<String, String> headers = new TreeMap<>();
        headers.put(NJAMS_MESSAGEVERSION_HTTP_HEADER, MessageVersion.V4.toString());
        headers.put(NJAMS_MESSAGEVERSION_HEADER, MessageVersion.V4.toString());
        headers.put(NJAMS_PATH_HTTP_HEADER, msg.getPath());
        headers.put(NJAMS_PATH_HEADER, msg.getPath());
        if (clientSessionId != null) {
            headers.put(NJAMS_CLIENTID_HTTP_HEADER, clientSessionId);
            headers.put(NJAMS_CLIENTID_HEADER, clientSessionId);
        }
        final String msgType;
        if (msg instanceof LogMessage) {
            msgType = MESSAGETYPE_EVENT;
            headers.put(NJAMS_LOGID_HTTP_HEADER, ((LogMessage) msg).getLogId());
            headers.put(NJAMS_LOGID_HEADER, ((LogMessage) msg).getLogId());
        } else if (msg instanceof ProjectMessage) {
            msgType = MESSAGETYPE_PROJECT;
        } else if (msg instanceof TraceMessage) {
            msgType = MESSAGETYPE_TRACE;
        } else {
            throw new IllegalArgumentException("Unknown message type: " + msg.getClass());
        }
        headers.put(NJAMS_MESSAGETYPE_HTTP_HEADER, msgType);
        headers.put(NJAMS_MESSAGETYPE_HEADER, msgType);
        return headers;
    }

    private void tryToSend(final CommonMessage msg, final Map<String, String> headers)
        throws InterruptedException {
        boolean sent = false;
        int responseStatus = -1;
        int tries = 0;
        Exception exception = null;
        final String json = JsonUtils.serialize(msg);
        do {
            try {
                responseStatus = send(json, headers);
                if (responseStatus == 200 || responseStatus == 204) {
                    sent = true;
                }
            } catch (Exception ex) {
                LOG.trace("Failed to send to {}:\n{}\nheaders={}", url, json, headers, ex);
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
                        // this triggers reconnecting the command-receiver which is only necessary on communication issues
                        // but not on message error indicated by some error code response
                        throw new HttpSendException(url, exception);
                    }
                    throw new NjamsSdkRuntimeException("Error sending message with HTTP client URI "
                        + url + " Response status is: " + responseStatus);
                }
                Thread.sleep(EXCEPTION_IDLE_TIME);
            }
            exception = null;
            responseStatus = -1;

        } while (!sent);
    }

    private int send(final String msg, final Map<String, String> headers) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Send:\nheaders={}\nbody={}", headers, msg);
        }
        final Builder requestBuilder =
            new Request.Builder().url(url).post(RequestBody.create(msg, HttpClientFactory.MEDIA_TYPE_JSON));
        headers.entrySet().forEach(e -> requestBuilder.header(e.getKey(), e.getValue()));
        requestBuilder.header("Content-Length", String.valueOf(msg.getBytes("UTF-8").length));
        final Response response = client.newCall(requestBuilder.build()).execute();
        if (LOG.isTraceEnabled()) {
            LOG.trace("POST response: {}", response.code());
        }
        return response.code();
    }

    @Override
    public String getName() {
        return NAME;
    }

}
