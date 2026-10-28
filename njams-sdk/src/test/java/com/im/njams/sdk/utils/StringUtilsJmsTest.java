package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class StringUtilsJmsTest {

    public static class FakeNonTextMessage {
        final Map<String, Object> props = new LinkedHashMap<>();

        public Enumeration<String> getPropertyNames() throws Exception {
            return Collections.enumeration(props.keySet());
        }

        public Object getObjectProperty(String key) throws Exception {
            return props.get(key);
        }
    }

    public static class FakeTextMessage extends FakeNonTextMessage {
        String text = "body";

        public String getText() throws Exception {
            return text;
        }
    }

    private static FakeTextMessage textMessage(String text) {
        final FakeTextMessage m = new FakeTextMessage();
        m.props.put("k1", "v1");
        m.props.put("k2", 2);
        m.text = text;
        return m;
    }

    @Test
    public void messageToString_rendersClassPropertiesAndBody() {
        final FakeTextMessage m = textMessage("body");
        assertEquals(FakeTextMessage.class.getName() + "[properties={k1=v1, k2=2}; body=body]",
            StringUtils.messageToString((Object) m));
    }

    @Test
    public void messageToString_omitsBodyForANonTextMessage() {
        final FakeNonTextMessage m = new FakeNonTextMessage();
        m.props.put("k1", "v1");
        assertEquals(FakeNonTextMessage.class.getName() + "[properties={k1=v1}]",
            StringUtils.messageToString((Object) m));
    }

    @Test
    public void messageToString_fallsBackToValueOfForAPlainObject() {
        final Object plain = new Object() {
            @Override
            public String toString() {
                return "plain";
            }
        };
        assertEquals(plain.getClass().getName() + "[properties={}]", StringUtils.messageToString(plain));
    }

    @Test
    public void messageToString_returnsNullForNull() {
        assertNull(StringUtils.messageToString((Object) null));
        assertNull(StringUtils.headersToString((Object) null));
    }

    @Test
    public void headersToString_rendersClassAndPropertiesOnly() {
        assertEquals(FakeTextMessage.class.getName() + "[properties={k1=v1, k2=2}]",
            StringUtils.headersToString((Object) textMessage("body")));
    }

    @Test
    public void messageToString_abbreviatesABodyOverOneThousandCharacters() {
        final StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            longBody.append('x');
        }
        final String result = StringUtils.messageToString((Object) textMessage(longBody.toString()));
        assertEquals(FakeTextMessage.class.getName() + "[properties={k1=v1, k2=2}; body="
            + longBody.substring(0, 1000) + StringUtils.ELLIPSIS + "]", result);
    }
}
