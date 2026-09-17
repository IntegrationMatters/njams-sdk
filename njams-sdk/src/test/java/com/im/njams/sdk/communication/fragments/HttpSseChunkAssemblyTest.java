package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_MESSAGE_ID_HTTP_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_RECEIVER_HTTP_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.launchdarkly.eventsource.MessageEvent;

/**
 * Unit tests for {@link HttpSseChunkAssembly}: legacy vs JSON event-name header parsing and the
 * reassembly of chunked SSE events.
 */
public class HttpSseChunkAssemblyTest {

    private static MessageEvent event(String name, String data, String lastEventId) {
        MessageEvent event = mock(MessageEvent.class);
        when(event.getEventName()).thenReturn(name);
        when(event.getData()).thenReturn(data);
        when(event.getLastEventId()).thenReturn(lastEventId);
        return event;
    }

    @Test
    public void legacyEventNameBecomesReceiverHeader() {
        HttpSseChunkAssembly assembly = new HttpSseChunkAssembly();

        RawMessage result = assembly.resolve(event("someReceiver", "body", "id-1"));

        assertEquals("body", result.getBody());
        assertEquals("someReceiver", result.getHeader(NJAMS_RECEIVER_HTTP_HEADER));
        assertEquals("id-1", result.getHeader(NJAMS_MESSAGE_ID_HTTP_HEADER));
    }

    @Test
    public void blankEventNameYieldsEmptyHeadersSingleMessage() {
        HttpSseChunkAssembly assembly = new HttpSseChunkAssembly();

        RawMessage result = assembly.resolve(event("", "body", "id-1"));

        assertEquals("body", result.getBody());
        assertTrue(result.getHeaders().isEmpty());
    }

    @Test
    public void jsonEventNameSplitMessageIsReassembled() {
        HttpSseChunkAssembly assembly = new HttpSseChunkAssembly();
        String chunk1 = "{\"njams-chunks\":\"2\",\"njams-chunk-no\":\"1\",\"njams-chunk-message-key\":\"k\"}";
        String chunk2 = "{\"njams-chunks\":\"2\",\"njams-chunk-no\":\"2\",\"njams-chunk-message-key\":\"k\"}";

        assertNull(assembly.resolve(event(chunk1, "Hello", "id-1")));
        RawMessage result = assembly.resolve(event(chunk2, "World", "id-2"));

        assertEquals("HelloWorld", result.getBody());
    }
}
