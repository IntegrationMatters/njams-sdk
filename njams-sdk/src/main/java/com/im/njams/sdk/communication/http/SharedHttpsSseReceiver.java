package com.im.njams.sdk.communication.http;

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
 * Receiver, which shares a connection and has to pick the right messages from it.
 *
 * @author bwand
 */
public class SharedHttpsSseReceiver extends HttpsSseReceiver implements ShareableReceiver<Map<String, String>> {
    private static final Logger LOG = LoggerFactory.getLogger(SharedHttpsSseReceiver.class);

    private final SharedReceiverSupport<SharedHttpsSseReceiver, Map<String, String>> sharingSupport =
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
