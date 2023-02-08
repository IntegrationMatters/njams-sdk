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
import com.im.njams.sdk.settings.Settings;
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
    protected WebTarget target;
    protected SseEventSource source;
    protected ObjectMapper mapper;
    protected URL url;

    private String clientId = null;

    @Override
    public void init(Properties properties) {
        clientId = properties.getProperty(Settings.INTERNAL_PROPERTY_CLIENTID);
        try {
            url = createUrl(properties);
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("Unable to init HTTP Receiver", ex);
        }
        mapper = JsonSerializerFactory.getDefaultMapper();
    }

    protected URL createUrl(Properties properties) throws MalformedURLException {
        String base = properties.getProperty(BASE_URL);
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
            target = client.target(url.toString() + "/subscribe");
            source = SseEventSource.target(target).build();
            source.register(this::onMessage, this::onError);
            source.open();
            LOG.debug("Subscribed SSE receiver to {}", target.getUri());
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            LOG.error("Exception during registering Server Sent Event Endpoint.", e);
            throw new NjamsSdkRuntimeException("Exception during registering Server Sent Event Endpoint.", e);
        }
    }

    void onError(Throwable throwable) {
        LOG.debug("OnError called, cause: " + throwable.getMessage());
        try {
            // Sleep, because source.open() in connect always returns without exception;
            // It starts a thread for the connection, which will then call this onError Method on failure
            // Wait for end of connect method (and setting the connectionStatus to CONNECTED first.
            // Otherwise we will have a race condition here.
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            throw new NjamsSdkRuntimeException(e.getMessage());
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        // Cannot use the reconnect loop here, because there is no chance to find out, if a reconnect has
        // been successful inside the loop and it will always end after first iteration.
        connect();
    }

    void onMessage(InboundSseEvent event) {
        String id = event.getId();
        String payload = event.readData();
        LOG.debug("OnMessage called, id=" + id + " payload=" + payload);
        if (!isValidMessage(event)) {
            return;
        }
        Instruction instruction = null;
        try {
            instruction = mapper.readValue(payload, Instruction.class);
        } catch (IOException e) {
            LOG.error("Exception during receiving SSE Event.", e);
        }
        onInstruction(instruction);
        sendReply(id, instruction, clientId);
    }

    /**
     * Check, that the message is valid for this nJAMS client.
     *
     * @param event the Inbound Event
     * @return true, if event is valid and should be handled
     */
    protected boolean isValidMessage(InboundSseEvent event) {
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
        if (StringUtils.isNotBlank(clientId) && !njams.getClientId().equals(clientId)) {
            LOG.debug("Message is not for me! Client id from Message is: " + event.getComment() +
                    " but nJAMS Client id is: " + njams.getClientId());
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

    protected void sendReply(final String requestId, final Instruction instruction, String clientId) {
        final String responseId = UUID.randomUUID().toString();
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(url.toString() + "/reply");

            Invocation.Builder builder = target.request()
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/plain")
                    .header("njams-receiver", "server")
                    .header("njams-messagetype", "reply")
                    .header("njams-message-id", responseId)
                    .header("njams-reply-for", requestId)
                    // Additionally add old headers
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/plain")
                    .header("NJAMS_RECEIVER", "server")
                    .header("NJAMS_MESSAGETYPE", "reply")
                    .header("NJAMS_MESSAGE_ID", responseId)
                    .header("NJAMS_REPLY_FOR", requestId);
            if (clientId != null) {
                builder.header("njams-clientid", clientId);
            }
            Response response = builder.post(Entity.json(JsonUtils.serialize(instruction)));
            LOG.debug("Reply response status:" + response.getStatus());
        } finally {
            if (client != null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // do nothing
                }
                client.close();
            }
        }
    }
}
