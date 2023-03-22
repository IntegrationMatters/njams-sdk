/*
 * Copyright (c) 2021 Faiz & Siegeln Software GmbH
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;

import javax.jms.IllegalStateException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Receives SSE (server sent events) from nJAMS as HTTP Client Communication
 *
 * @author bwand
 */
public class HttpSseReceiver extends AbstractReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(HttpSseReceiver.class);
    private static final String NAME = "HTTP";
    protected static final String BASE_URL = NjamsSettings.PROPERTY_HTTP_BASE_URL;
    protected static final String SSE_API_PATH = "api/httpcommunication";

    protected Client client;
    protected SseEventSource source;
    protected final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
    protected URL url;
    private Throwable connectError = null;

    @Override
    public void init(final Properties properties) {
        try {
            url = createUrl(properties);
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTP Receiver", ex);
        }
    }

    private URL createUrl(final Properties properties) throws MalformedURLException {
        String base = properties.getProperty(BASE_URL);
        if (StringUtils.isBlank(base)) {
            throw new NjamsSdkRuntimeException("Required parameter " + BASE_URL + " is missing.");
        }
        if (base.charAt(base.length() - 1) != '/') {
            base += "/";
        }
        return new URL(base + SSE_API_PATH);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void connect() {
        if (isConnected()) {
            return;
        }
        try {
            connectionStatus = ConnectionStatus.CONNECTING;
            client = ClientBuilder.newClient();
            final WebTarget target = client.target(url.toString() + "/subscribe");
            source = SseEventSource.target(target).build();
            source.register(this::onMessage, this::onError);
            connectError = null;
            synchronized (this) {
                LOG.debug("Start connect...");
                source.open();
                /* 
                 * source.open() has no reliable error handling, i.e., it is not possible to determine whether
                 * source.open() was successful or not. This is because the actual connection is created in a
                 * separate thread and when source.open() completes and connection failed, neither the on-error
                 * callback was reliably called before, nor the connection state has been set to "closed".
                 * As a workaround, we wait for at most 1 second here to allow either of the above mentioned
                 * error handling to occur in parallel.
                 * 
                 */
                long wait = 1000;
                final long waitEnd = System.currentTimeMillis() + wait;
                while (wait > 0) {
                    if (connectError != null || !source.isOpen()) {
                        LOG.debug("Connect failed: {}", connectError.toString());
                        throw connectError != null ? connectError
                                : new IllegalStateException("Event source is closed.");
                    }
                    wait(wait);
                    wait = waitEnd - System.currentTimeMillis();
                }
                connectError = null;
                connectionStatus = ConnectionStatus.CONNECTED;
                LOG.debug("Subscribed SSE receiver to {}", target.getUri());
            }
        } catch (final Throwable e) {
            throw new NjamsSdkRuntimeException("Exception during registering SSE endpoint.", e);
        }
    }

    protected synchronized void onError(final Throwable throwable) {
        LOG.debug("OnError called, cause: {} (status={})", throwable, connectionStatus);
        connectError = throwable;

        // trigger the reconnect thread only if connection breaks, not when already trying to re-connect.
        final boolean triggerReconnect = connectionStatus == ConnectionStatus.CONNECTED;
        connectionStatus = ConnectionStatus.DISCONNECTED;
        notifyAll();
        if (triggerReconnect) {
            onException(throwable instanceof Exception ? (Exception) throwable
                    : new NjamsSdkRuntimeException("Connection failed.", throwable));
        }
    }

    protected void onMessage(final InboundSseEvent event) {
        final String id = event.getId();
        final String payload = event.readData();
        LOG.debug("OnMessage called, event-id={}, payload={}", id, payload);
        if (!isValidMessage(event)) {
            return;
        }
        Instruction instruction = null;
        try {
            instruction = mapper.readValue(payload, Instruction.class);
        } catch (final IOException e) {
            LOG.error("Failed to parse instruction from SSE event.", e);
            return;
        }
        if (suppressGetRequestHandlerInstruction(instruction, njams)) {
            return;
        }
        onInstruction(instruction);
        sendReply(id, instruction, njams.getClientSessionId());
    }

    /**
     * Check, that the message is valid for this nJAMS client.
     *
     * @param event the inbound event
     * @return true, if event is valid and should be handled
     */
    protected boolean isValidMessage(final InboundSseEvent event) {
        if (event == null) {
            return false;
        }
        final String receiver = event.getName();
        if (StringUtils.isBlank(receiver) || !njams.getClientPath().equals(new Path(receiver))) {
            LOG.debug("Message is not for me! Client path from Message is: " + event.getName() +
                    " but nJAMS Client path is: " + njams.getClientPath());
            return false;
        }
        final String clientId = event.getComment();
        if (StringUtils.isNotBlank(clientId) && !njams.getCommunicationSessionId().equals(clientId)) {
            LOG.debug("Message is not for me! Client id from Message is: " + event.getComment() +
                    " but nJAMS Client id is: " + njams.getCommunicationSessionId());
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        if (!isConnected()) {
            return;
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        source.close();
    }

    protected void sendReply(final String requestId, final Instruction instruction, final String clientId) {
        final String responseId = UUID.randomUUID().toString();
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            final WebTarget target = client.target(url.toString() + "/reply");

            final Invocation.Builder builder = target.request()
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/plain")
                    .header("njams-receiver", "server")
                    .header("njams-messagetype", "reply")
                    .header("njams-message-id", responseId)
                    .header("njams-reply-for", requestId)
                    // Additionally add old headers
                    .header("NJAMS_RECEIVER", "server")
                    .header("NJAMS_MESSAGETYPE", "reply")
                    .header("NJAMS_MESSAGE_ID", responseId)
                    .header("NJAMS_REPLY_FOR", requestId);
            if (clientId != null) {
                builder.header("njams-clientid", clientId);
            }
            final Response response = builder.post(Entity.json(JsonUtils.serialize(instruction)));
            LOG.debug("Reply response status:" + response.getStatus());
        } finally {
            if (client != null) {
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                    // do nothing
                }
                client.close();
            }
        }
    }
}
