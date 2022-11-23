package com.im.njams.sdk.communication.http;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.ws.rs.sse.InboundSseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

/**
 * Receiver, which shares a connection and has to pick the right messages from it.
 *
 * @author bwand
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
                HttpsSender.initializeSSLContext(properties.getProperty(SSL_CERTIFIACTE_FILE));
            url = new URL(properties.getProperty(BASE_URL) + SSE_API_PATH);
        } catch (final Exception ex) {
            throw new NjamsSdkRuntimeException("unable to init https sse receiver", ex);
        }
        mapper = JsonSerializerFactory.getDefaultMapper();
    }

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
    public String getClientId(InboundSseEvent requestMessage, Instruction instruction) {
        return requestMessage.getComment();
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
    public void sendReply(InboundSseEvent event, Instruction reply, String clientId) {
        sendReply(event.getId(), reply, clientId);
    }

}
