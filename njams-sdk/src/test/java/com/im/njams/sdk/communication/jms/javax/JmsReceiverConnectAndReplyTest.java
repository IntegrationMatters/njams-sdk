package com.im.njams.sdk.communication.jms.javax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.communication.fragments.SplitSupport;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Connect, filter and reply paths of {@link JmsReceiver} against a scripted, fully mocked JMS API (SDK-499).
 */
public class JmsReceiverConnectAndReplyTest {

    private static final String PING = "{\"request\":{\"command\":\"ping\"}}";

    private Njams njams;
    private JmsReceiver receiver;
    private final List<Instruction> handled = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() {
        ScriptedJmsFactory.reset();
        handled.clear();
        njams = new Njams(Path.of("SDK4", "JMS", "client"), "1.0", "test", TestSender.getSettings());
        njams.commands().add(instruction -> {
            handled.add(instruction);
            instruction.setResponseResultCode(0);
            instruction.setResponseResultMessage("pong");
        });
        receiver = new JmsReceiver();
    }

    @After
    public void tearDown() {
        receiver.setShouldShutdown(true);
        receiver.cancelReconnect();
    }

    @Test
    public void connectWithoutCredentials() throws Exception {
        connect(settings());

        verify(ScriptedJmsFactory.connectionFactory()).createConnection();
        verify(ScriptedJmsFactory.consumer()).setMessageListener(receiver);
        verify(ScriptedJmsFactory.connection()).setExceptionListener(receiver);
        verify(ScriptedJmsFactory.connection()).start();
        assertTrue(receiver.isConnected());
    }

    @Test
    public void connectWithCredentials() throws Exception {
        ClientSettings settings = settings();
        settings.put(NjamsSettings.PROPERTY_JMS_USERNAME, "user");
        settings.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "secret");

        connect(settings);

        verify(ScriptedJmsFactory.connectionFactory()).createConnection("user", "secret");
        verify(ScriptedJmsFactory.connectionFactory(), never()).createConnection();
        assertTrue(receiver.isConnected());
    }

    @Test
    public void connectWithMessageSelectorCreatesSelectiveConsumer() throws Exception {
        connect(settings());

        ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
        verify(ScriptedJmsFactory.session()).createConsumer(eq(ScriptedJmsFactory.topic()), selector.capture());
        assertTrue(selector.getValue().contains("NJAMS_RECEIVER = '" + clientPath() + "'"));
        verify(ScriptedJmsFactory.session(), never()).createConsumer(any());
    }

    @Test
    public void connectWithoutMessageSelectorCreatesPlainConsumer() throws Exception {
        connect(settingsWithoutSelector());

        verify(ScriptedJmsFactory.session()).createConsumer(ScriptedJmsFactory.topic());
        verify(ScriptedJmsFactory.session(), never()).createConsumer(any(), anyString());
    }

    @Test
    public void failingConnectClosesEverythingAndThrows() throws Exception {
        when(ScriptedJmsFactory.connection().createSession(anyBoolean(), anyInt()))
            .thenThrow(new JMSException("no session"));
        receiver.init(settings());
        receiver.setNjams(njams);

        try {
            receiver.connect();
            fail("connect must fail");
        } catch (NjamsSdkRuntimeException expected) {
            assertEquals("Unable to initialize", expected.getMessage());
        }

        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(receiver.isDisconnected());
    }

    @Test
    public void stopReportsButSurvivesFailingClose() throws Exception {
        connect(settings());
        doThrow(new JMSException("consumer")).when(ScriptedJmsFactory.consumer()).close();
        doThrow(new JMSException("session")).when(ScriptedJmsFactory.session()).close();
        doThrow(new JMSException("connection")).when(ScriptedJmsFactory.connection()).close();

        receiver.stop();

        verify(ScriptedJmsFactory.consumer()).close();
        verify(ScriptedJmsFactory.session()).close();
        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(receiver.isDisconnected());
    }

    @Test
    public void messageFilterWithoutSelector() throws Exception {
        connect(settingsWithoutSelector());
        String parent = njams.metadata().getClientPath().getParent().toString();

        receiver.onMessage(command(PING, clientPath(), System.currentTimeMillis()));
        receiver.onMessage(command(PING, parent, System.currentTimeMillis()));
        assertEquals(2, handled.size());

        receiver.onMessage(command(PING, ">SDK4>OTHER>", System.currentTimeMillis()));
        receiver.onMessage(command(PING, clientPath(), 1L));
        TextMessage broken = command(PING, clientPath(), System.currentTimeMillis());
        when(broken.propertyExists(anyString())).thenThrow(new JMSException("broken"));
        receiver.onMessage(broken);

        assertEquals(2, handled.size());
    }

    @Test
    public void messageFilterWithSelector() throws Exception {
        connect(settings());

        receiver.onMessage(command(PING, clientPath(), System.currentTimeMillis()));
        assertEquals(1, handled.size());

        // what the broker-side selector would not deliver is dropped by the filter, too
        receiver.onMessage(command(PING, ">SDK4>OTHER>", System.currentTimeMillis()));
        receiver.onMessage(command(PING, clientPath(), 1L));

        assertEquals(1, handled.size());
    }

    @Test
    public void onMessageRepliesToReplyDestination() throws Exception {
        connect(settings());
        Destination replyTo = mock(Destination.class);
        TextMessage request = command(PING, clientPath(), System.currentTimeMillis());
        when(request.getJMSReplyTo()).thenReturn(replyTo);
        when(request.getJMSCorrelationID()).thenReturn("corr-1");

        receiver.onMessage(request);

        assertEquals(1, handled.size());
        verify(ScriptedJmsFactory.session()).createProducer(replyTo);
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(ScriptedJmsFactory.producer()).send(sent.capture());
        verify(sent.getValue()).setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER,
            njams.metadata().getClientSessionId());
        verify(sent.getValue()).setJMSCorrelationID("corr-1");
        verify(ScriptedJmsFactory.producer()).close();
    }

    @Test
    public void onMessageIgnoresNonJsonContent() throws Exception {
        connect(settings());
        TextMessage xml = command(PING, clientPath(), System.currentTimeMillis());
        when(xml.getStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER)).thenReturn("xml");

        assertIgnored(xml);
    }

    @Test
    public void onMessageIgnoresForeignClient() throws Exception {
        connect(settings());
        TextMessage foreign = command(PING, clientPath(), System.currentTimeMillis());
        when(foreign.getStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER)).thenReturn("another-client");

        assertIgnored(foreign);
    }

    @Test
    public void onMessageIgnoresInvalidInstruction() throws Exception {
        connect(settings());

        assertIgnored(command("this is not json", clientPath(), System.currentTimeMillis()));
    }

    private void assertIgnored(TextMessage message) throws JMSException {
        receiver.onMessage(message);

        assertTrue(handled.isEmpty());
        verify(ScriptedJmsFactory.session(), never()).createProducer(any());
    }

    @Test
    public void replyIsSplitIntoChunks() throws Exception {
        ClientSettings settings = settings();
        settings.put(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, String.valueOf(SplitSupport.MIN_SIZE_LIMIT));
        njams.commands().add(instruction -> instruction.setResponseResultMessage(large()));
        connect(settings);

        receiver.onMessage(command(PING, clientPath(), System.currentTimeMillis()));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(ScriptedJmsFactory.producer(), atLeast(2)).send(sent.capture());
        String chunks = String.valueOf(sent.getAllValues().size());
        for (Message chunk : sent.getAllValues()) {
            verify(chunk).setStringProperty(MessageHeaders.NJAMS_CHUNKS_HEADER, chunks);
        }
    }

    @Test
    public void replyToClosedDestinationIsIgnored() throws Exception {
        connect(settings());
        Destination replyTo = mock(Destination.class);

        doThrow(new InvalidDestinationException("gone")).when(ScriptedJmsFactory.session()).createProducer(any());
        receiver.reply(replyTo, null, new Instruction(), "id");

        doThrow(new JMSException("broken")).when(ScriptedJmsFactory.session()).createProducer(any());
        receiver.reply(replyTo, null, new Instruction(), "id");

        doReturn(ScriptedJmsFactory.producer()).when(ScriptedJmsFactory.session()).createProducer(any());
        doThrow(new JMSException("close")).when(ScriptedJmsFactory.producer()).close();
        receiver.reply(replyTo, "", new Instruction(), "id");

        verify(ScriptedJmsFactory.producer()).send(any(Message.class));
        verify(ScriptedJmsFactory.producer()).close();
    }

    @Test
    public void onExceptionTriggersReconnect() throws Exception {
        connect(settings());

        receiver.onException(new JMSException("x"));

        verify(ScriptedJmsFactory.consumer()).close();
        verify(ScriptedJmsFactory.session()).close();
        verify(ScriptedJmsFactory.connection()).close();
        // waiting for the reconnect also ends the reconnect thread before the next test resets the mocks
        verify(ScriptedJmsFactory.connectionFactory(), timeout(10_000).times(2)).createConnection();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!receiver.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(receiver.isConnected());
    }

    private void connect(ClientSettings settings) {
        receiver.init(settings);
        receiver.setNjams(njams);
        receiver.connect();
    }

    private String clientPath() {
        return njams.metadata().getClientPath().toString();
    }

    private static TextMessage command(String body, String receiverPath, long timestamp) throws JMSException {
        TextMessage message = mock(TextMessage.class);
        when(message.getText()).thenReturn(body);
        when(message.getStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER)).thenReturn("json");
        when(message.propertyExists(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(true);
        when(message.getStringProperty(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(receiverPath);
        when(message.getJMSTimestamp()).thenReturn(timestamp);
        return message;
    }

    private static String large() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 3 * SplitSupport.MIN_SIZE_LIMIT) {
            sb.append("0123456789");
        }
        return sb.toString();
    }

    private static ClientSettings settings() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ScriptedJmsFactory.NAME);
        settings.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams.test");
        return settings;
    }

    private static ClientSettings settingsWithoutSelector() {
        ClientSettings settings = settings();
        settings.put(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR, "false");
        return settings;
    }
}
