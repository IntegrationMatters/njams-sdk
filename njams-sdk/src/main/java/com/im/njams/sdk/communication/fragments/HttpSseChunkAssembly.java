package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.CONTENT_TYPE_JSON;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CONTENT_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGE_ID_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HTTP_HEADER;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.utils.JsonUtils;
import com.im.njams.sdk.utils.StringUtils;
import com.launchdarkly.eventsource.MessageEvent;

/**
 * HTTP server sent events implementation working on {@link MessageEvent} messages.
 */
public class HttpSseChunkAssembly extends GenericChunkAssembly<MessageEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpSseChunkAssembly.class);

    public HttpSseChunkAssembly() {
        super(null,
            HttpSseChunkAssembly::getHeaders,
            MessageEvent::getData,
            true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getHeaders(MessageEvent event) {
        final String name = event.getEventName();
        if (StringUtils.isBlank(name)) {
            LOG.error("Received unnamed event: {}", event);
            return Collections.emptyMap();
        }
        if (name.trim().charAt(0) == '{') {
            LOG.debug("Assuming JSON event {}: {}", event.getLastEventId(), name);
            return JsonUtils.parse(name, HashMap.class);
        }
        LOG.debug("Assuming legacy event {}: {}", event.getLastEventId(), name);
        final Map<String, String> map = new HashMap<>();
        map.put(NJAMS_MESSAGE_ID_HTTP_HEADER, event.getLastEventId());
        map.put(NJAMS_RECEIVER_HTTP_HEADER, name);
        map.put(NJAMS_CONTENT_HTTP_HEADER, CONTENT_TYPE_JSON);
        return map;
    }

}
