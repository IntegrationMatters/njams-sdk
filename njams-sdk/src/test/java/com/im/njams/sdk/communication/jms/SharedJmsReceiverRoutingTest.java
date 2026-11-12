package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Routing, consumer maintenance and message handling of {@link SharedJmsReceiver} against a scripted, fully
 * mocked JMS API (SDK-499).
 */
public class SharedJmsReceiverRoutingTest {

    private static final String PING = "{\"request\":{\"command\":\"ping\"}}";

    private SharedJmsReceiver receiver;
    private Njams first;
    private Njams second;
    private final List<Instruction> firstHandled = Collections.synchronizedList(new ArrayList<>());
    private final List<Instruction> secondHandled = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() {
        ScriptedJmsFactory.reset();
        firstHandled.clear();
        secondHandled.clear();
        first = client("first", firstHandled);
        second = client("second", secondHandled);
        receiver = new SharedJmsReceiver();
        receiver.init(settings());
    }

    @After
    public void tearDown() {
        receiver.setShouldShutdown(true);
        receiver.cancelReconnect();
    }

    @Test
    public void instructionForRegisteredClientIsAnswered() throws Exception {
        connectBoth();

        receiver.onMessage(command(PING, pathOf(second)));

        assertTrue(firstHandled.isEmpty());
        assertEquals(1, secondHandled.size());
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(ScriptedJmsFactory.producer()).send(sent.capture());
        verify(sent.getValue()).setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER,
            second.metadata().getClientSessionId());
        verify(sent.getValue()).setJMSCorrelationID("corr-" + pathOf(second));
    }

    @Test
    public void instructionForUnknownClientIsAnsweredWith99() throws Exception {
        connectBoth();

        // a shared ancestor path passes the selector and filter, but matches no registered client
        receiver.onMessage(command(PING, ">SDK4>JMS>"));

        assertTrue(firstHandled.isEmpty());
        assertTrue(secondHandled.isEmpty());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(ScriptedJmsFactory.session()).createTextMessage(body.capture());
        assertTrue(body.getValue(), body.getValue().contains("\"resultCode\":99"));
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(ScriptedJmsFactory.producer()).send(sent.capture());
        verify(sent.getValue()).setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER, null);
    }

    @Test
    public void addingAndRemovingClientsRecreatesTheSelectiveConsumer() throws Exception {
        receiver.setNjams(first);
        verify(ScriptedJmsFactory.session(), never()).createConsumer(any(), anyString());

        receiver.connect();
        verify(ScriptedJmsFactory.session(), times(1)).createConsumer(any(), anyString());

        receiver.setNjams(second);
        verify(ScriptedJmsFactory.consumer(), times(1)).close();
        verify(ScriptedJmsFactory.session(), times(2)).createConsumer(any(), anyString());

        assertFalse(receiver.removeNjams(second));
        verify(ScriptedJmsFactory.consumer(), times(2)).close();
        verify(ScriptedJmsFactory.session(), times(3)).createConsumer(any(), anyString());

        assertTrue(receiver.removeNjams(first));
        verify(ScriptedJmsFactory.consumer(), times(3)).close();
        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(receiver.isDisconnected());
        verify(ScriptedJmsFactory.session(), never()).createConsumer(any());
    }

    @Test
    public void failingConsumerCloseReconnectsWithTheCurrentSelector() throws Exception {
        receiver.setNjams(first);
        receiver.connect();
        doThrow(new JMSException("close")).when(ScriptedJmsFactory.consumer()).close();

        receiver.setNjams(second);

        verify(ScriptedJmsFactory.connectionFactory(), timeout(10_000).times(2)).createConnection();
        verify(ScriptedJmsFactory.session(), timeout(10_000))
            .createConsumer(eq(ScriptedJmsFactory.topic()), argThat(selector -> selector.contains(pathOf(second))));
    }

    @Test
    public void failingConsumerCreationReconnectsWithTheCurrentSelector() throws Exception {
        receiver.setNjams(first);
        receiver.connect();
        doThrow(new JMSException("create")).doReturn(ScriptedJmsFactory.consumer())
            .when(ScriptedJmsFactory.session()).createConsumer(any(), anyString());

        receiver.setNjams(second);

        verify(ScriptedJmsFactory.connectionFactory(), timeout(10_000).times(2)).createConnection();
        // the failed attempt and the one made by the reconnect
        verify(ScriptedJmsFactory.session(), timeout(10_000).times(2))
            .createConsumer(eq(ScriptedJmsFactory.topic()), argThat(selector -> selector.contains(pathOf(second))));
    }

    @Test
    public void removingOneOfSeveralClientsRecreatesTheConsumerWithoutIt() throws Exception {
        connectBoth();

        assertFalse(receiver.removeNjams(second));

        verify(ScriptedJmsFactory.session()).createConsumer(eq(ScriptedJmsFactory.topic()),
            argThat(selector -> selector.contains(pathOf(first)) && !selector.contains(pathOf(second))));
    }

    @Test
    public void commandForRemovedClientIsNeitherHandledNorAnswered() throws Exception {
        connectBoth();
        assertFalse(receiver.removeNjams(second));

        receiver.onMessage(command(PING, pathOf(second)));

        assertTrue(secondHandled.isEmpty());
        verify(ScriptedJmsFactory.session(), never()).createProducer(any());

        receiver.onMessage(command(PING, pathOf(first)));

        assertEquals(1, firstHandled.size());
    }

    @Test
    public void commandForRemovedClientIsNeitherHandledNorAnsweredWithoutSelector() throws Exception {
        receiver = new SharedJmsReceiver();
        ClientSettings settings = settings();
        settings.put(NjamsSettings.PROPERTY_JMS_SUPPORTS_MESSAGE_SELECTOR, "false");
        receiver.init(settings);
        connectBoth();
        assertFalse(receiver.removeNjams(second));

        receiver.onMessage(command(PING, pathOf(second)));

        assertTrue(secondHandled.isEmpty());
        verify(ScriptedJmsFactory.session(), never()).createProducer(any());

        receiver.onMessage(command(PING, pathOf(first)));

        assertEquals(1, firstHandled.size());
    }

    @Test
    public void clientAddedWhileConnectingIsInTheSelectorOfTheResultingConsumer() throws Exception {
        final Thread[] adder = new Thread[1];
        receiver = new SharedJmsReceiver() {
            @Override
            protected MessageConsumer createConsumer(Session sess, Topic topic) throws JMSException {
                final MessageConsumer created = super.createConsumer(sess, topic);
                if (adder[0] == null) {
                    // a second client starts after this connect has created its consumer, but before it completes
                    adder[0] = new Thread(() -> setNjams(second));
                    adder[0].start();
                    awaitBlockedOrTerminated(adder[0]);
                }
                return created;
            }
        };
        receiver.init(settings());
        receiver.setNjams(first);

        receiver.connect();
        adder[0].join(10_000);

        assertFalse(adder[0].isAlive());
        verify(ScriptedJmsFactory.session()).createConsumer(eq(ScriptedJmsFactory.topic()),
            argThat(selector -> selector.contains(pathOf(first)) && selector.contains(pathOf(second))));
    }

    private static void awaitBlockedOrTerminated(Thread thread) {
        final long deadline = System.currentTimeMillis() + 10_000;
        while (thread.getState() != Thread.State.BLOCKED && thread.getState() != Thread.State.TERMINATED
            && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
    }

    @Test
    public void nonJsonMessageIsIgnored() throws Exception {
        connectBoth();
        TextMessage xml = command(PING, pathOf(first));
        when(xml.getStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER)).thenReturn("xml");

        assertIgnored(xml);
    }

    @Test
    public void invalidInstructionIsIgnored() throws Exception {
        connectBoth();

        assertIgnored(command("this is not json", pathOf(first)));
    }

    @Test
    public void nonTextMessageIsIgnored() throws Exception {
        connectBoth();
        Message notText = mock(Message.class);
        when(notText.getStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER)).thenReturn("json");
        when(notText.propertyExists(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(true);
        when(notText.getStringProperty(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(pathOf(first));

        assertIgnored(notText);
    }

    private void assertIgnored(Message message) throws JMSException {
        receiver.onMessage(message);

        assertTrue(firstHandled.isEmpty());
        assertTrue(secondHandled.isEmpty());
        verify(ScriptedJmsFactory.session(), never()).createProducer(any());
    }

    @Test
    public void receiverPathAndClientIdFailuresYieldNull() throws Exception {
        connectBoth();
        Message broken = mock(Message.class);
        when(broken.getStringProperty(anyString())).thenThrow(new JMSException("broken"));
        when(broken.getJMSReplyTo()).thenThrow(new JMSException("broken"));

        assertNull(receiver.getReceiverPath(broken, null));
        assertNull(receiver.getClientId(broken, null));
        receiver.sendReply(broken, new Instruction(), "id");

        verify(ScriptedJmsFactory.session(), never()).createProducer(any());
    }

    private void connectBoth() {
        receiver.setNjams(first);
        receiver.setNjams(second);
        receiver.connect();
    }

    private static Njams client(String name, List<Instruction> handled) {
        Njams njams = new Njams(Path.of("SDK4", "JMS", name), "1.0", "test", TestSender.getSettings());
        njams.commands().add(instruction -> {
            handled.add(instruction);
            instruction.setResponseResultCode(0);
            instruction.setResponseResultMessage("pong");
        });
        return njams;
    }

    private static String pathOf(Njams njams) {
        return njams.metadata().getClientPath().toString();
    }

    private static TextMessage command(String body, String receiverPath) throws JMSException {
        TextMessage message = mock(TextMessage.class);
        when(message.getText()).thenReturn(body);
        when(message.getStringProperty(MessageHeaders.NJAMS_CONTENT_HEADER)).thenReturn("json");
        when(message.propertyExists(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(true);
        when(message.getStringProperty(MessageHeaders.NJAMS_RECEIVER_HEADER)).thenReturn(receiverPath);
        when(message.getJMSReplyTo()).thenReturn(mock(Destination.class));
        when(message.getJMSCorrelationID()).thenReturn("corr-" + receiverPath);
        return message;
    }

    private static ClientSettings settings() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ScriptedJmsFactory.NAME);
        settings.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams.test");
        return settings;
    }
}
