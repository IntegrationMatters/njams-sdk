package com.im.njams.sdk.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;

public class JmsMessageAccessTest {

    public static class FakeNonTextMessage {
        final Map<String, Object> props = new HashMap<>();

        public Enumeration<String> getPropertyNames() throws Exception {
            return Collections.enumeration(props.keySet());
        }

        public Object getObjectProperty(String key) throws Exception {
            return props.get(key);
        }

        public void setStringProperty(String key, String value) throws Exception {
            props.put(key, value);
        }
    }

    public static class FakeTextMessage extends FakeNonTextMessage {
        public String getText() throws Exception {
            return "body";
        }
    }

    public static class PlainObject {
    }

    public interface FakeMessageApi {
        Enumeration<String> getPropertyNames() throws Exception;
    }

    static class FakeHiddenMessage implements FakeMessageApi {
        @Override
        public Enumeration<String> getPropertyNames() {
            return Collections.enumeration(Arrays.asList("a", "b"));
        }
    }

    public static class FakeThrowingMessage {
        public Enumeration<String> getPropertyNames() throws Exception {
            throw new Exception("provider failure");
        }
    }

    private static FakeTextMessage textMessage() {
        FakeTextMessage m = new FakeTextMessage();
        m.props.put("k1", "v1");
        m.props.put("k2", 2);
        return m;
    }

    @Test
    public void isMessage_isTrueForSomethingWithPropertyNames() {
        assertTrue(JmsMessageAccess.isMessage(textMessage()));
    }

    @Test
    public void isMessage_isFalseForAPlainObject() {
        assertFalse(JmsMessageAccess.isMessage(new PlainObject()));
    }

    @Test
    public void isTextMessage_isFalseWhenGetTextIsAbsent() {
        assertFalse(JmsMessageAccess.isTextMessage(new FakeNonTextMessage()));
        assertTrue(JmsMessageAccess.isTextMessage(textMessage()));
    }

    @Test
    public void propertyNames_returnsAllKeys() {
        assertEquals(new HashSet<>(Arrays.asList("k1", "k2")),
            new HashSet<>(JmsMessageAccess.propertyNames(textMessage())));
    }

    @Test
    public void objectProperty_returnsTheValue() {
        assertEquals(2, JmsMessageAccess.objectProperty(textMessage(), "k2"));
        assertNull(JmsMessageAccess.objectProperty(textMessage(), "missing"));
    }

    @Test
    public void text_returnsTheBody() {
        assertEquals("body", JmsMessageAccess.text(textMessage()));
    }

    @Test
    public void setStringProperty_setsTheValue() {
        FakeTextMessage m = textMessage();
        JmsMessageAccess.setStringProperty(m, "x", "y");
        assertEquals("y", m.props.get("x"));
    }

    @Test
    public void text_returnsNullForANonTextMessage() {
        assertNull(JmsMessageAccess.text(new FakeNonTextMessage()));
    }

    @Test
    public void propertyNames_returnsEmptyWhenTheProviderThrows() {
        Collection<String> names = JmsMessageAccess.propertyNames(new FakeThrowingMessage());
        assertTrue(names.isEmpty());
    }

    @Test
    public void propertyNames_worksOnAPackagePrivateImplementation() {
        assertEquals(Arrays.asList("a", "b"), JmsMessageAccess.propertyNames(new FakeHiddenMessage()));
    }

    @Test
    public void probingIsDoneOncePerClass_evenWhenAccessorsAreAbsent() {
        FakeNonTextMessage m = new FakeNonTextMessage();
        Object before = JmsMessageAccess.descriptorFor(FakeNonTextMessage.class);
        assertFalse(JmsMessageAccess.isTextMessage(m));
        assertFalse(JmsMessageAccess.isTextMessage(m));
        assertSame(before, JmsMessageAccess.descriptorFor(FakeNonTextMessage.class));
    }
}
