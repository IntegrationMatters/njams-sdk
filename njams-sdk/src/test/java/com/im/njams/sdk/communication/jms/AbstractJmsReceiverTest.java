package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Drives the namespace-free receiver base through a subclass whose hooks only record calls — no JMS types involved.
 */
public class AbstractJmsReceiverTest {

    private static class RecordingReceiver extends AbstractJmsReceiver {
        ClientSettings resolveFactoryArgument;
        Boolean inheritedSettingsFieldWasNullAtResolveFactory;
        Collection<Njams> instances = Collections.emptyList();
        List<Exception> closeResult = new ArrayList<>();
        int closeAllCalls;
        final List<Exception> reported = new ArrayList<>();

        @Override
        protected void resolveFactory(ClientSettings settings) {
            resolveFactoryArgument = settings;
            inheritedSettingsFieldWasNullAtResolveFactory = this.settings == null;
        }

        @Override
        protected Predicate<Object> createMessageFilter() {
            return m -> true;
        }

        @Override
        protected List<Exception> closeAll() {
            closeAllCalls++;
            connectionStatus = ConnectionStatus.DISCONNECTED;
            return closeResult;
        }

        @Override
        protected void printExceptionsOnClose(List<Exception> exceptions) {
            reported.addAll(exceptions);
            super.printExceptionsOnClose(exceptions);
        }

        @Override
        protected Collection<Njams> provideNjamsInstances() {
            return instances;
        }

        @Override
        public void connect() {
            // not under test
        }

        void markConnected() {
            connectionStatus = ConnectionStatus.CONNECTED;
        }
    }

    private static Njams njams(String... pathElements) {
        final Njams njams = mock(Njams.class);
        final NjamsMetadata metadata = mock(NjamsMetadata.class);
        when(njams.metadata()).thenReturn(metadata);
        when(metadata.getClientPath()).thenReturn(Path.of(pathElements));
        return njams;
    }

    private static ClientSettings settings(String... keyValues) {
        final Properties p = new Properties();
        p.setProperty(NjamsSettings.PROPERTY_COMMUNICATION, AbstractJmsSender.COMMUNICATION_NAME);
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return ClientSettings.from(p);
    }

    private static RecordingReceiver receiver(String... keyValues) {
        final RecordingReceiver receiver = new RecordingReceiver();
        receiver.init(settings(keyValues));
        return receiver;
    }

    @Test
    public void init_callsResolveFactoryWithTheSettingsBeforeTheyAreAssignedToTheInheritedField() {
        final ClientSettings settings = settings();
        final RecordingReceiver receiver = new RecordingReceiver();

        receiver.init(settings);

        assertSame("resolveFactory must receive the settings init was called with", settings,
            receiver.resolveFactoryArgument);
        assertEquals("resolveFactory must run before super.init assigns the inherited settings field",
            Boolean.TRUE, receiver.inheritedSettingsFieldWasNullAtResolveFactory);
    }

    @Test
    public void createMessageSelector_joinsEveryAncestorPathOfEveryInstance() {
        final RecordingReceiver receiver = receiver();
        receiver.instances = Arrays.asList(njams("SDK4", "TEST"), njams("OTHER"));

        assertEquals("NJAMS_RECEIVER = '>OTHER>' OR NJAMS_RECEIVER = '>SDK4>' OR NJAMS_RECEIVER = '>SDK4>TEST>'",
            receiver.createMessageSelector());
    }

    @Test
    public void createMessageSelector_returnsNullWhenSelectorsAreDisabled() {
        final RecordingReceiver receiver = receiver(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR, "false");
        receiver.instances = Collections.singleton(njams("SDK4", "TEST"));

        assertNull(receiver.createMessageSelector());
    }

    @Test
    public void createMessageSelector_returnsNullWithNoInstances() {
        final RecordingReceiver receiver = receiver();
        receiver.instances = Collections.emptyList();

        assertNull(receiver.createMessageSelector());
    }

    @Test
    public void init_derivesTheCommandsTopicFromTheDestinationPrefix() {
        assertEquals("njams.commands", receiver().topicName);
        assertEquals("a.b.commands", receiver(NjamsSettings.PROPERTY_JMS_DESTINATION, "a.b").topicName);
        assertEquals("c.d.commands", receiver(NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX, "c.d").topicName);
    }

    @Test
    public void init_prefersAnExplicitCommandsDestination() {
        final RecordingReceiver receiver = receiver(NjamsSettings.PROPERTY_JMS_DESTINATION, "a.b",
            NjamsSettings.PROPERTY_JMS_COMMANDS_DESTINATION, "explicit.commands");

        assertEquals("explicit.commands", receiver.topicName);
    }

    @Test
    public void stop_reportsEveryExceptionFromCloseAll() {
        final RecordingReceiver receiver = receiver();
        final Exception first = new NjamsSdkRuntimeException("first", new IllegalStateException("cause"));
        final Exception second = new IllegalArgumentException("second");
        receiver.closeResult = Arrays.asList(first, second);
        receiver.markConnected();

        receiver.stop();

        assertEquals(1, receiver.closeAllCalls);
        assertEquals(Arrays.asList(first, second), receiver.reported);
        assertTrue(receiver.isDisconnected());
    }

    @Test
    public void stop_doesNothingWhileDisconnected() {
        final RecordingReceiver receiver = receiver();

        receiver.stop();

        assertEquals(0, receiver.closeAllCalls);
    }

    @Test
    public void getInstruction_returnsNullForAnInstructionWithoutARequest() {
        final RecordingReceiver receiver = receiver();

        assertNull(receiver.getInstruction(new FakeTextMessage("{}")));
        assertNull(receiver.getInstruction(new FakeTextMessage("no json at all")));

        final Instruction instruction =
            receiver.getInstruction(new FakeTextMessage("{\"request\":{\"command\":\"test\"}}"));
        assertNotNull(instruction);
        assertEquals("test", instruction.getRequest().getCommand());
    }

    @Test
    public void getInstruction_returnsNullForAnythingThatIsNotATextMessage() {
        final RecordingReceiver receiver = receiver();

        assertNull(receiver.getInstruction(new Object()));
    }

    /**
     * Duck-typed stand-in for a JMS {@code TextMessage}: {@link JmsMessageAccess} resolves these three methods
     * reflectively, so the test needs no JMS type at all.
     */
    public static class FakeTextMessage {
        private final String text;

        FakeTextMessage(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public Enumeration<String> getPropertyNames() {
            return Collections.enumeration(Collections.emptyList());
        }

        public Object getObjectProperty(String key) {
            return null;
        }
    }
}
