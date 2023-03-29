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

import static com.im.njams.sdk.communication.MessageHeaders.CONTENT_TYPE_JSON;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CLIENTID_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CONTENT_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGE_ID_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HTTP_HEADER;

import java.util.Map;

import javax.ws.rs.sse.InboundSseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Overrides the common {@link HttpSseReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author bwand
 */
public class SharedHttpSseReceiver extends HttpSseReceiver implements ShareableReceiver<Map<String, String>> {
    private static final Logger LOG = LoggerFactory.getLogger(SharedHttpSseReceiver.class);

    private final SharedReceiverSupport<SharedHttpSseReceiver, Map<String, String>> sharingSupport =
            new SharedReceiverSupport<>(this);

    /**
     * Adds the given instance to this receiver for receiving instructions.
     *
     * @see com.im.njams.sdk.communication.jms.JmsReceiver#setNjams(com.im.njams.sdk.Njams)
     */
    @Override
    public void setNjams(final Njams njams) {
        super.setNjams(null);
        sharingSupport.addNjams(njams);
    }

    @Override
    public void removeNjams(Njams njams) {
        sharingSupport.removeNjams(njams);
    }

    @Override
    public Path getReceiverPath(Map<String, String> eventHeaders, Instruction instruction) {
        return new Path(eventHeaders.get(NJAMS_RECEIVER_HTTP_HEADER));
    }

    @Override
    public String getClientId(Map<String, String> eventHeaders, Instruction instruction) {
        return eventHeaders.get(NJAMS_CLIENTID_HTTP_HEADER);
    }

    /**
     * This method is the MessageListener implementation. It receives events automatically.
     *
     * @param event the new arrived event
     */
    @Override
    protected void onMessage(final InboundSseEvent event) {
        LOG.debug("OnMessage called, event-id={}", event.getId());
        final Map<String, String> eventHeaders = parseEventHeaders(event);
        if (!isValidMessage(eventHeaders)) {
            return;
        }
        final String id = eventHeaders.get(NJAMS_MESSAGE_ID_HTTP_HEADER);
        final String payload = event.readData();
        LOG.debug("Processing event {} (headers={}, payload={})", id, eventHeaders, payload);
        Instruction instruction = null;
        try {
            instruction = JsonUtils.parse(payload, Instruction.class);
        } catch (final Exception e) {
            LOG.error("Failed to parse instruction from SSE event.", e);
            return;
        }
        sharingSupport.onInstruction(eventHeaders, instruction, false);
    }

    @Override
    protected boolean isValidMessage(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        final String receiver = headers.get(NJAMS_RECEIVER_HTTP_HEADER);
        if (StringUtils.isBlank(receiver)) {
            LOG.debug("Missing receiver path");
            return false;
        }
        final String messageId = headers.get(NJAMS_MESSAGE_ID_HTTP_HEADER);
        if (StringUtils.isBlank(messageId)) {
            LOG.debug("No message ID in event");
            return false;
        }
        if (!CONTENT_TYPE_JSON.equalsIgnoreCase(headers.get(NJAMS_CONTENT_HTTP_HEADER))) {
            LOG.debug("Received non json event -> ignore");
            return false;
        }

        return true;
    }

    @Override
    public void sendReply(Map<String, String> eventHeaders, Instruction reply, String clientId) {
        sendReply(eventHeaders.get(NJAMS_MESSAGE_ID_HTTP_HEADER), reply, clientId);
    }

}
