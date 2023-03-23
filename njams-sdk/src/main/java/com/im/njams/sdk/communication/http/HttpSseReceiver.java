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

import java.net.URI;
import java.net.URISyntaxException;
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
import com.im.njams.sdk.NjamsSettings;
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
    protected static final String SSE_API_PATH = "api/httpcommunication/";

    protected Client client = null;
    protected SseEventSource source = null;
    protected URI subscribeUri = null;
    protected URI replyUri = null;
    private Throwable connectError = null;

    @Override
    public void init(final Properties properties) {
        try {
            subscribeUri = createUri(properties, "subscribe");
            replyUri = createUri(properties, "reply");
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTP receiver", ex);
        }
        LOG.debug("URI subscription={}; reply={}", subscribeUri, replyUri);
    }

    private URI createUri(final Properties properties, String path) throws URISyntaxException {
        String base = properties.getProperty(NjamsSettings.PROPERTY_HTTP_BASE_URL);
        if (StringUtils.isBlank(base)) {
            throw new NjamsSdkRuntimeException(
                    "Required parameter " + NjamsSettings.PROPERTY_HTTP_BASE_URL + " is missing.");
        }
        if (base.charAt(base.length() - 1) != '/') {
            base += "/";
        }
        return new URI(base + SSE_API_PATH + path);
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
            client = createClient();
            final WebTarget target = client.target(subscribeUri);
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
            close();
            throw new NjamsSdkRuntimeException("Exception during registering SSE endpoint.", e);
        }
    }

    protected Client createClient() {
        LOG.debug("Creating new http client.");
        return ClientBuilder.newClient();
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
            instruction = JsonUtils.parse(payload, Instruction.class);
        } catch (final Exception e) {
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
            LOG.debug("Message is not for me! Client path from message is: " + event.getName() +
                    " but nJAMS client path is: " + njams.getClientPath());
            return false;
        }
        final String clientId = event.getComment();
        if (StringUtils.isNotBlank(clientId) && !njams.getCommunicationSessionId().equals(clientId)) {
            LOG.debug("Message is not for me! Client id from message is: " + event.getComment() +
                    " but nJAMS client id is: " + njams.getCommunicationSessionId());
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
        close();
    }

    private void close() {
        if (source != null) {
            source.close();
            source = null;
        }
        if (client != null) {
            client.close();
            client = null;
            LOG.debug("Client closed");
        }
    }

    protected void sendReply(final String requestId, final Instruction instruction, final String clientId) {
        final String replyId = UUID.randomUUID().toString();
        final String json = JsonUtils.serialize(instruction);
        LOG.trace("Sending reply {} (clientId={}) for request {}:\n{}", replyId, clientId, requestId, json);
        final WebTarget target = client.target(replyUri);

        final Invocation.Builder builder = target.request()
                .header("Content-Type", "application/json")
                .header("Accept", "text/plain")
                .header("njams-receiver", "server")
                .header("njams-messagetype", "reply")
                .header("njams-message-id", replyId)
                .header("njams-reply-for", requestId)
                // Additionally add old headers
                .header("NJAMS_RECEIVER", "server")
                .header("NJAMS_MESSAGETYPE", "reply")
                .header("NJAMS_MESSAGE_ID", replyId)
                .header("NJAMS_REPLY_FOR", requestId);
        if (clientId != null) {
            builder.header("njams-clientid", clientId);
        }
        final Response response = builder.post(Entity.json(json));
        LOG.debug("Response status for reply {}: {}", replyId, response.getStatus());
    }
}
