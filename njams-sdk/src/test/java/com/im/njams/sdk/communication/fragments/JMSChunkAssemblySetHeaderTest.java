package com.im.njams.sdk.communication.fragments;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class JMSChunkAssemblySetHeaderTest {

    public static class FakeMessage {
        final Map<String, String> props = new HashMap<>();

        public void setStringProperty(String key, String value) throws Exception {
            props.put(key, value);
        }
    }

    @Test
    public void setHeader_objectFormSetsTheProperty() {
        final FakeMessage m = new FakeMessage();
        JMSChunkAssembly.setHeader(m, "k", "v");
        assertEquals("v", m.props.get("k"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void setHeader_deprecatedFormSetsTheProperty() throws Exception {
        final javax.jms.Message m = org.mockito.Mockito.mock(javax.jms.Message.class);
        JMSChunkAssembly.setHeader(m, "k", "v");
        org.mockito.Mockito.verify(m).setStringProperty("k", "v");
    }
}
