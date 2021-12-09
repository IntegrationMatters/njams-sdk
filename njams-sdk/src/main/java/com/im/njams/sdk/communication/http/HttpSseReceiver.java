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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;

/**
 * Receives SSE (server sent events) from nJAMS as HTTP Client Communication
 *
 * @author bwand
 */
public class HttpSseReceiver extends AbstractReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(HttpSseReceiver.class);
    private static final String NAME = "HTTP";
    private static final String PROPERTY_PREFIX = "njams.sdk.communication.http";
    private static final String BASE_URL = PROPERTY_PREFIX + ".base.url";
    private static final String SSE_API_PATH = "api/httpcommunication";

    private Client client;
    protected WebTarget target;
    protected SseEventSource source;
    protected ObjectMapper mapper;
    private URL url;

    @Override
    public void init(Properties properties) {
        try {
            url = new URL(properties.getProperty(BASE_URL) + SSE_API_PATH);
        } catch (final MalformedURLException ex) {
            throw new NjamsSdkRuntimeException("unable to init http sender", ex);
        }
        client = ClientBuilder.newClient();
        target = client.target(url.toString() + "/subscribe");
        mapper = JsonSerializerFactory.getDefaultMapper();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void start() {
        connect();
    }

    @Override
    public void connect() {
        try {
            source = SseEventSource.target(target).build();
            source.register(this::onMessage);
            source.open();
        } catch (Exception e) {
            LOG.error("Exception during registering SSE.", e);
        }
    }

    void onMessage(InboundSseEvent event) {
        String id = event.getId();
        String payload = event.readData();
        LOG.debug("OnMessage called, id=" + id + " payload=" + payload);
        Instruction instruction = null;
        try {
            instruction = mapper.readValue(payload, Instruction.class);
        } catch (IOException e) {
            LOG.error("Exception during receiving SSE Event.", e);
        }
        onInstruction(instruction);
        sendReply(id, instruction);
    }

    @Override
    public void stop() {
        source.close();
    }

    protected void sendReply(final String requestId, final Instruction instruction) {
        final String responseId = UUID.randomUUID().toString();
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url.toString() + "/reply");
        Response response = target.request()
                .header("Content-Type", "application/json")
                .header("Accept", "text/plain")
                .header("NJAMS_RECEIVER", "server")
                .header("NJAMS_MESSAGETYPE", "reply")
                .header("NJAMS_MESSAGE_ID", responseId)
                .header("NJAMS_REPLY_FOR", requestId)
                .post(Entity.json(JsonUtils.serialize(instruction)));
        LOG.debug("Reply response status:" + response.getStatus());
    }
}
