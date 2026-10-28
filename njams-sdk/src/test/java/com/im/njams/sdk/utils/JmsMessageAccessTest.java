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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
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

    private static class CapturingAppender extends AppenderSkeleton {
        final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }

    private Logger logger;
    private Level originalLevel;
    private CapturingAppender appender;

    @Before
    public void captureLog() {
        logger = Logger.getLogger(JmsMessageAccess.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new CapturingAppender();
        logger.addAppender(appender);
    }

    @After
    public void restoreLog() {
        logger.removeAppender(appender);
        logger.setLevel(originalLevel);
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
        Collection<String> names = JmsMessageAccess.propertyNames(new FakeThrowingMessage() {
        });
        assertTrue(names.isEmpty());
    }

    @Test
    public void providerFailure_logsTheCauseNotTheReflectionWrapper() {
        JmsMessageAccess.propertyNames(new FakeThrowingMessage() {
        });
        assertEquals(1, appender.events.size());
        Throwable logged = appender.events.get(0).getThrowableInformation().getThrowable();
        assertEquals("provider failure", logged.getMessage());
        assertFalse(logged instanceof java.lang.reflect.InvocationTargetException);
    }

    @Test
    public void providerFailure_isLoggedAtWarnOncePerClassThenAtDebugWithoutStackTrace() {
        FakeThrowingMessage message = new FakeThrowingMessage() {
        };
        JmsMessageAccess.propertyNames(message);
        JmsMessageAccess.propertyNames(message);
        JmsMessageAccess.propertyNames(message);
        assertEquals(3, appender.events.size());
        assertEquals(Level.WARN, appender.events.get(0).getLevel());
        assertEquals(Level.DEBUG, appender.events.get(1).getLevel());
        assertEquals(Level.DEBUG, appender.events.get(2).getLevel());
        assertNull(appender.events.get(1).getThrowableInformation());
    }

    @Test
    public void propertyNames_worksOnAPackagePrivateImplementation() {
        assertEquals(Arrays.asList("a", "b"), JmsMessageAccess.propertyNames(new FakeHiddenMessage()));
        assertSame(FakeMessageApi.class,
            JmsMessageAccess.getPropertyNamesMethod(FakeHiddenMessage.class).getDeclaringClass());
    }

    @Test
    public void probingIsDoneOncePerClass_evenWhenAccessorsAreAbsent() {
        FakeNonTextMessage m = new FakeNonTextMessage();
        Object before = JmsMessageAccess.descriptorFor(FakeNonTextMessage.class);
        assertNull(JmsMessageAccess.getTextMethod(FakeNonTextMessage.class));
        assertTrue(JmsMessageAccess.getPropertyNamesMethod(FakeNonTextMessage.class) != null);
        assertFalse(JmsMessageAccess.isTextMessage(m));
        assertFalse(JmsMessageAccess.isTextMessage(m));
        assertSame(before, JmsMessageAccess.descriptorFor(FakeNonTextMessage.class));
        assertSame(JmsMessageAccess.getPropertyNamesMethod(FakeNonTextMessage.class),
            JmsMessageAccess.getPropertyNamesMethod(FakeNonTextMessage.class));
    }
}
