package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Drives the namespace-free sender base through a subclass whose hooks only record calls — no JMS types involved.
 */
public class AbstractJmsSenderTest {

    private static class FakeMessage {
        final String logId;
        final String body;
        final Map<String, String> properties = new HashMap<>();

        FakeMessage(String logId, String body) {
            this.logId = logId;
            this.body = body;
        }
    }

    private static class RecordingSender extends AbstractJmsSender {
        final List<FakeMessage> sent = new ArrayList<>();
        String data = "0123456789";
        ClientSettings resolveFactoryArgument;
        Boolean inheritedSettingsFieldWasNullAtResolveFactory;

        @Override
        protected void resolveFactory(ClientSettings settings) {
            resolveFactoryArgument = settings;
            inheritedSettingsFieldWasNullAtResolveFactory = this.settings == null;
        }

        @Override
        protected Object buildMessage(String logId, String path, String body, String messageType,
            String clientSessionId) {
            return new FakeMessage(logId, body);
        }

        @Override
        protected void setProperty(Object message, String key, String value) {
            ((FakeMessage) message).properties.put(key, value);
        }

        @Override
        protected void tryToSend(Object producer, Object message) {
            sent.add((FakeMessage) message);
        }

        @Override
        protected String describeDestination(Object producer) {
            return "fake";
        }

        @Override
        protected Class<?> congestionExceptionType() {
            return IllegalStateException.class;
        }

        @Override
        protected String serialize(CommonMessage msg) {
            return data;
        }

        @Override
        protected void send(LogMessage msg, String clientSessionId) {
            // not under test
        }

        @Override
        protected void send(ProjectMessage msg, String clientSessionId) {
            // not under test
        }

        @Override
        protected void send(TraceMessage msg, String clientSessionId) {
            // not under test
        }
    }

    private static RecordingSender sender(String maxMessageSize, String... keyValues) {
        final Properties p = new Properties();
        p.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, AbstractJmsSender.COMMUNICATION_NAME);
        p.setProperty(SplitSupport.TESTING_NO_LIMIT_CHECKS, "true");
        if (maxMessageSize != null) {
            p.setProperty(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, maxMessageSize);
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        final RecordingSender s = new RecordingSender();
        s.init(ClientSettings.from(p));
        return s;
    }

    @Test
    public void init_callsResolveFactoryWithTheSettingsBeforeTheyAreAssignedToTheInheritedField() {
        final Properties p = new Properties();
        p.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, AbstractJmsSender.COMMUNICATION_NAME);
        p.setProperty(SplitSupport.TESTING_NO_LIMIT_CHECKS, "true");
        final ClientSettings settings = ClientSettings.from(p);
        final RecordingSender s = new RecordingSender();

        s.init(settings);

        assertSame("resolveFactory must receive the settings init was called with", settings,
            s.resolveFactoryArgument);
        assertEquals("resolveFactory must run before super.init assigns the inherited settings field",
            Boolean.TRUE, s.inheritedSettingsFieldWasNullAtResolveFactory);
    }

    private static LogMessage logMessage() {
        final LogMessage m = new LogMessage();
        m.setLogId("log-1");
        m.setPath("p");
        return m;
    }

    @Test
    public void sendMessage_buildsOneMessageWhenSplittingIsOff() throws Exception {
        final RecordingSender s = sender(null);
        s.sendMessage(new Object(), logMessage(), "event", "session");
        assertEquals(1, s.sent.size());
        assertEquals("0123456789", s.sent.get(0).body);
        assertEquals("log-1", s.sent.get(0).logId);
        assertTrue(s.sent.get(0).properties.isEmpty());
    }

    @Test
    public void sendChunks_buildsOneMessagePerChunkWithTheSameMessageKey() throws Exception {
        final RecordingSender s = sender("20");
        s.data = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        final ProjectMessage msg = new ProjectMessage();
        msg.setPath("p");
        s.sendMessage(new Object(), msg, "project", "session");
        assertTrue(s.sent.size() > 1);
        final StringBuilder all = new StringBuilder();
        String key = null;
        for (int i = 0; i < s.sent.size(); i++) {
            final FakeMessage m = s.sent.get(i);
            all.append(m.body);
            assertNull(m.logId);
            assertEquals(String.valueOf(i + 1), m.properties.get(MessageHeaders.NJAMS_CHUNK_NO_HEADER));
            assertEquals(String.valueOf(s.sent.size()), m.properties.get(MessageHeaders.NJAMS_CHUNKS_HEADER));
            final String k = m.properties.get(MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HEADER);
            assertNotNull(k);
            if (key == null) {
                key = k;
            }
            assertEquals(key, k);
        }
        assertEquals(s.data, all.toString());
    }

    @Test
    public void sendChunks_usesTheLogIdAsMessageKeyForALogMessage() throws Exception {
        final RecordingSender s = sender("20");
        s.data = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRS";
        s.sendMessage(new Object(), logMessage(), "event", "session");
        assertTrue(s.sent.size() > 1);
        for (FakeMessage m : s.sent) {
            assertEquals("log-1", m.logId);
            assertEquals("log-1", m.properties.get(MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HEADER));
        }
    }

    @Test
    public void isCongestion_walksTheCauseChainForTheConfiguredExceptionType() {
        final RecordingSender s = sender(null);
        assertTrue(s.isCongestion(new IllegalStateException("full")));
        assertTrue(s.isCongestion(new RuntimeException("wrapper", new Exception(new IllegalStateException()))));
        assertFalse(s.isCongestion(new RuntimeException("other")));
        assertFalse(s.isCongestion(null));
    }

    @Test
    public void isCongestion_returnsFalseOnASelfReferencingCauseChain() {
        final RecordingSender s = sender(null);
        final Exception selfCaused = new Exception("loop") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(s.isCongestion(selfCaused));
    }

    @Test
    public void destinationPrefix_fallsBackToNjamsWhenUnset() {
        assertEquals("njams", sender(null).destinationPrefix());
        assertEquals("a.b", sender(null, NjamsSettings.PROPERTY_JMS_DESTINATION, "a.b").destinationPrefix());
        assertEquals("c.d", sender(null, NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX, "c.d").destinationPrefix());
    }

    @Test
    public void resolveDeliveryMode_mapsNonPersistentReliableAndDefault() {
        // javax/jakarta DeliveryMode: NON_PERSISTENT = 1, PERSISTENT = 2; 22 is Tibco RELIABLE
        assertEquals(1, sender(null, NjamsSettings.PROPERTY_JMS_DELIVERY_MODE, "NON_PERSISTENT").resolveDeliveryMode());
        assertEquals(1, sender(null, NjamsSettings.PROPERTY_JMS_DELIVERY_MODE, "nonpersistent").resolveDeliveryMode());
        assertEquals(22, sender(null, NjamsSettings.PROPERTY_JMS_DELIVERY_MODE, "RELIABLE").resolveDeliveryMode());
        assertEquals(2, sender(null).resolveDeliveryMode());
    }
}
