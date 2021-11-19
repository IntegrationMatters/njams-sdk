package com.im.njams.sdk.communication.http;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.settings.encoding.Transformer;
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
    private static final String RECEIVER_URL = PROPERTY_PREFIX + ".receiver.url";

    private Client client;
    private WebTarget target;
    private SseEventSource source;
    protected ObjectMapper mapper;
    private URL url;

    @Override
    public void init(Properties properties) {
        try {
            url = new URL(properties.getProperty(RECEIVER_URL));
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
        LOG.info("OnMessage called.");
        String id = event.getId();
        String name = event.getName();
        String payload = event.readData();
        String comment = event.getComment();
        LOG.info("id:" + id);
        LOG.info("payload:" + payload);
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

    private void sendReply(final String requestId, final Instruction instruction) {
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
        LOG.info("Reply response status:" + response.getStatus());
    }
}
