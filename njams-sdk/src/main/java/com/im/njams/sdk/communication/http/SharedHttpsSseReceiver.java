/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.http;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import com.im.njams.sdk.communication.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;

/**
 * Receiver, which shares a connection and has to pick the right messages from it.
 */
public class SharedHttpsSseReceiver extends HttpsSseReceiver implements ShareableReceiver<InboundSseEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(SharedHttpsSseReceiver.class);

    private SSLContext sslContext;

    private final SharedReceiverSupport<SharedHttpsSseReceiver, InboundSseEvent> sharingSupport =
            new SharedReceiverSupport<>(this);

    @Override
    public void init(Properties properties) {
        try {
            sslContext =
                    HttpsSender.initializeSSLContext(properties.getProperty(PROPERTY_PREFIX + SSL_CERTIFIACTE_FILE));
            url = new URL(properties.getProperty(BASE_URL) + SSE_API_PATH);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init https sse receiver", ex);
        }
        mapper = JsonSerializerFactory.getDefaultMapper();
    }

    /**
     * Adds the given instance to this receiver for receiving instructions.
     */
    @Override
    public void addReceiver(Receiver receiver) {
        sharingSupport.addReceiver(receiver);
    }

    @Override
    public void removeReceiver(Receiver receiver) {
        sharingSupport.removeReceiver(receiver);
    }

    @Override
    public Path getReceiverPath(InboundSseEvent requestMessage, Instruction instruction) {
        return new Path(requestMessage.getName());
    }

    @Override
    public void connect() {
        try {
            client = ClientBuilder.newBuilder().sslContext(sslContext).build();
            target = client.target(url.toString() + "/subscribe");
            source = SseEventSource.target(target).build();
            source.register(this::onMessage);
            source.open();
            LOG.debug("Subscribed SSE receiver to {}", target.getUri());
        } catch (Exception e) {
            LOG.error("Exception during registering Server Sent Event Endpoint.", e);
        }
    }

    /**
     * This method is the MessageListener implementation. It receives events automatically.
     *
     * @param event the new arrived event
     */
    @Override
    void onMessage(InboundSseEvent event) {
        String id = event.getId();
        String payload = event.readData();
        LOG.debug("OnMessage in shared receiver called, id=" + id + " payload=" + payload);
        Instruction instruction = null;
        try {
            instruction = mapper.readValue(payload, Instruction.class);
        } catch (IOException e) {
            LOG.error("Exception during receiving SSE Event.", e);
        }
        sharingSupport.onInstruction(event, instruction, false);
    }

    @Override
    public void sendReply(InboundSseEvent event, Instruction reply) {
        sendReply(event.getId(), reply);
    }

}
