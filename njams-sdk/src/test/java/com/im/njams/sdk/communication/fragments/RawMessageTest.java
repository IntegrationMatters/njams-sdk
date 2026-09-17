package com.im.njams.sdk.communication.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link RawMessage}, the immutable headers + body container.
 */
public class RawMessageTest {

    private static Map<String, String> headers(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void gettersReturnConstructorValues() {
        Map<String, String> headers = headers("a", "1");
        RawMessage msg = new RawMessage(headers, "body");
        assertEquals(headers, msg.getHeaders());
        assertEquals("body", msg.getBody());
    }

    @Test
    public void getHeaderReturnsValueOrNull() {
        RawMessage msg = new RawMessage(headers("a", "1"), "body");
        assertEquals("1", msg.getHeader("a"));
        assertNull(msg.getHeader("missing"));
    }

    @Test
    public void getHeaderWithNullHeadersReturnsNull() {
        RawMessage msg = new RawMessage(null, "body");
        assertNull(msg.getHeader("any"));
        assertNull(msg.getHeaders());
    }

    @Test
    public void toStringContainsShortBodyVerbatim() {
        RawMessage msg = new RawMessage(headers("a", "1"), "short-body");
        String s = msg.toString();
        assertTrue(s.contains("short-body"));
        assertTrue(s.contains("headers="));
    }

    @Test
    public void toStringTruncatesLongBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append('x');
        }
        RawMessage msg = new RawMessage(null, sb.toString());
        String s = msg.toString();
        // long bodies are truncated to 500 chars plus an ellipsis
        assertTrue(s.contains("..."));
        assertTrue(s.length() < 600);
    }
}
