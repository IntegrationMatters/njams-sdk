package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNKS_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_NO_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.junit.Test;

/**
 * Unit tests for {@link JMSChunkAssembly}: header extraction from JMS properties, body extraction
 * and resilience to {@link JMSException}s.
 */
public class JMSChunkAssemblyTest {

    private static TextMessage textMessage(Map<String, String> props, String text) throws JMSException {
        TextMessage m = mock(TextMessage.class);
        when(m.getPropertyNames()).thenReturn(Collections.enumeration(props.keySet()));
        for (Map.Entry<String, String> e : props.entrySet()) {
            when(m.getObjectProperty(e.getKey())).thenReturn(e.getValue());
        }
        when(m.getText()).thenReturn(text);
        return m;
    }

    private static Map<String, String> chunkProps(int total, int no, String key) {
        Map<String, String> m = new HashMap<>();
        m.put(NJAMS_CHUNKS_HEADER, String.valueOf(total));
        m.put(NJAMS_CHUNK_NO_HEADER, String.valueOf(no));
        m.put(NJAMS_CHUNK_MESSAGE_KEY_HEADER, key);
        return m;
    }

    @Test
    public void singleMessageExposesPropertiesAsHeaders() throws JMSException {
        JMSChunkAssembly assembly = new JMSChunkAssembly();
        Map<String, String> props = new HashMap<>();
        props.put("foo", "bar");

        RawMessage result = assembly.resolve(textMessage(props, "body"));

        assertEquals("body", result.getBody());
        assertEquals("bar", result.getHeader("foo"));
    }

    @Test
    public void splitMessageIsReassembled() throws JMSException {
        JMSChunkAssembly assembly = new JMSChunkAssembly();
        assertNull(assembly.resolve(textMessage(chunkProps(2, 1, "k"), "Hello")));
        RawMessage result = assembly.resolve(textMessage(chunkProps(2, 2, "k"), "World"));
        assertEquals("HelloWorld", result.getBody());
    }

    @Test
    public void propertyNamesFailureYieldsEmptyHeadersSingleMessage() throws JMSException {
        JMSChunkAssembly assembly = new JMSChunkAssembly();
        TextMessage m = mock(TextMessage.class);
        when(m.getPropertyNames()).thenThrow(new JMSException("boom"));
        when(m.getText()).thenReturn("body");

        RawMessage result = assembly.resolve(m);

        // no headers could be read -> treated as a single message with empty headers
        assertEquals("body", result.getBody());
        assertTrue(result.getHeaders().isEmpty());
    }

    @Test
    public void getTextFailureYieldsNullBody() throws JMSException {
        JMSChunkAssembly assembly = new JMSChunkAssembly();
        TextMessage m = mock(TextMessage.class);
        when(m.getPropertyNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(m.getText()).thenThrow(new JMSException("boom"));

        RawMessage result = assembly.resolve(m);

        assertNull(result.getBody());
    }

    @Test
    public void setHeaderSetsStringProperty() throws JMSException {
        TextMessage m = mock(TextMessage.class);
        JMSChunkAssembly.setHeader(m, "key", "value");
        verify(m).setStringProperty("key", "value");
    }

    @Test
    public void setHeaderSwallowsJmsException() throws JMSException {
        TextMessage m = mock(TextMessage.class);
        doThrow(new JMSException("boom")).when(m).setStringProperty("key", "value");
        // must not propagate
        JMSChunkAssembly.setHeader(m, "key", "value");
    }
}
