package com.im.njams.sdk.communication.http;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.io.IOException;

/**
 * Overrides the common {@link HttpSseReceiver} for supporting receiving messages for multiple {@link Njams} instances.
 *
 * @author bwand
 */
public class SharedHttpSseReceiver extends HttpSseReceiver implements ShareableReceiver<InboundSseEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(SharedHttpSseReceiver.class);

    private final SharedReceiverSupport<SharedHttpSseReceiver, InboundSseEvent> sharingSupport =
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
    public Path getReceiverPath(InboundSseEvent requestMessage, Instruction instruction) {
        return new Path(requestMessage.getName());
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

    /**
     * This method is the MessageListener implementation. It receives events automatically.
     *
     * @param event the new arrived event
     */
    @Override
    void onMessage(InboundSseEvent event) {
        LOG.info("OnMessage in shared receiver called.");
        String id = event.getId();
        String payload = event.readData();
        LOG.info("id:" + id);
        LOG.info("payload:" + payload);
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
