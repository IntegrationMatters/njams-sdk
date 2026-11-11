package com.im.njams.sdk.communication.jms.javax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.communication.SenderFailureSinkProbe;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Connect, send and close paths of {@link JmsSender} against a scripted, fully mocked JMS API (SDK-499).
 */
public class JmsSenderConnectAndSendTest {

    private static final String SESSION_ID = "session-1";

    private final MessageProducer eventProducer = mock(MessageProducer.class);
    private final MessageProducer projectProducer = mock(MessageProducer.class);
    private JmsSender sender;

    @Before
    public void setUp() throws Exception {
        ScriptedJmsFactory.reset();
        doReturn(eventProducer, projectProducer).when(ScriptedJmsFactory.session()).createProducer(any());
        sender = new NoDelayJmsSender();
    }

    @After
    public void tearDown() {
        sender.close();
    }

    @Test
    public void connectWithAndWithoutCredentials() throws Exception {
        ClientSettings blank = settings();
        blank.put(NjamsSettings.PROPERTY_JMS_USERNAME, " ");
        blank.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "secret");
        connect(blank);
        verify(ScriptedJmsFactory.connectionFactory()).createConnection();
        verify(ScriptedJmsFactory.connection()).setExceptionListener(sender);
        assertTrue(sender.isConnected());
        sender.close();

        ClientSettings credentials = settings();
        credentials.put(NjamsSettings.PROPERTY_JMS_USERNAME, "user");
        credentials.put(NjamsSettings.PROPERTY_JMS_PASSWORD, "secret");
        sender = new NoDelayJmsSender();
        connect(credentials);
        verify(ScriptedJmsFactory.connectionFactory()).createConnection("user", "secret");
        assertTrue(sender.isConnected());
    }

    @Test
    public void sharedProducerWhenSelectorsSupported() throws Exception {
        connect(settings());

        assertEquals(Arrays.asList("njams.test.event"), ScriptedJmsFactory.queueNames());
        verify(eventProducer).setDeliveryMode(DeliveryMode.PERSISTENT);
        verify(ScriptedJmsFactory.session(), times(1)).createProducer(any());
    }

    @Test
    public void separateProjectProducerWithoutSelectors() throws Exception {
        ClientSettings settings = settingsWithoutSelector();
        settings.put(NjamsSettings.PROPERTY_JMS_DELIVERY_MODE, "NON_PERSISTENT");
        connect(settings);

        assertEquals(Arrays.asList("njams.test.event", "njams.test.project"), ScriptedJmsFactory.queueNames());
        verify(eventProducer).setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        verify(projectProducer).setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    }

    @Test
    public void failingConnectCleansUpAndThrows() throws Exception {
        doThrow(new JMSException("no producer")).when(ScriptedJmsFactory.session()).createProducer(any());
        expectConnectFailure();
        verify(ScriptedJmsFactory.session()).close();
        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(sender.isDisconnected());

        ScriptedJmsFactory.reset();
        doThrow(new JMSException("no producer")).when(ScriptedJmsFactory.session()).createProducer(any());
        doThrow(new JMSException("session")).when(ScriptedJmsFactory.session()).close();
        doThrow(new JMSException("connection")).when(ScriptedJmsFactory.connection()).close();
        sender = new NoDelayJmsSender();
        expectConnectFailure();
        assertTrue(sender.isDisconnected());
    }

    @Test
    public void sendsEachMessageTypeWithHeaders() throws Exception {
        connect(settingsWithoutSelector());
        LogMessage log = new LogMessage();
        log.setLogId("log-1");
        log.setPath(">SDK4>JMS>process>");
        ProjectMessage project = new ProjectMessage();
        project.setPath(">SDK4>JMS>");
        TraceMessage trace = new TraceMessage();
        trace.setPath(">SDK4>JMS>");

        sender.send(log, SESSION_ID);
        sender.send(project, SESSION_ID);
        sender.send(trace, SESSION_ID);

        Message sentLog = sentBy(eventProducer).get(0);
        verify(sentLog).setStringProperty(MessageHeaders.NJAMS_LOGID_HEADER, "log-1");
        verify(sentLog).setStringProperty(MessageHeaders.NJAMS_MESSAGEVERSION_HEADER, "V4");
        verify(sentLog).setStringProperty(MessageHeaders.NJAMS_MESSAGETYPE_HEADER, MessageHeaders.MESSAGETYPE_EVENT);
        verify(sentLog).setStringProperty(MessageHeaders.NJAMS_PATH_HEADER, ">SDK4>JMS>process>");
        verify(sentLog).setStringProperty(MessageHeaders.NJAMS_CLIENTID_HEADER, SESSION_ID);

        List<Message> sentProject = sentBy(projectProducer);
        assertEquals(2, sentProject.size());
        verify(sentProject.get(0)).setStringProperty(MessageHeaders.NJAMS_MESSAGETYPE_HEADER,
            MessageHeaders.MESSAGETYPE_PROJECT);
        verify(sentProject.get(1)).setStringProperty(MessageHeaders.NJAMS_MESSAGETYPE_HEADER,
            MessageHeaders.MESSAGETYPE_TRACE);
        for (Message m : sentProject) {
            verify(m, never()).setStringProperty(eq(MessageHeaders.NJAMS_LOGID_HEADER), anyString());
        }
    }

    @Test
    public void sendFailureIsWrapped() throws Exception {
        connect(settings());
        doThrow(new JMSException("broken")).when(eventProducer).send(any(Message.class));

        expectSendFailure(new LogMessage(), "Unable to send LogMessage");
        expectSendFailure(new ProjectMessage(), "Unable to send ProjectMessage");
        expectSendFailure(new TraceMessage(), "Unable to send TraceMessage");
    }

    @Test
    public void closeReleasesProducersSessionAndConnection() throws Exception {
        connect(settings());
        doThrow(new JMSException("producer")).when(eventProducer).close();
        doThrow(new JMSException("session")).when(ScriptedJmsFactory.session()).close();
        doThrow(new JMSException("connection")).when(ScriptedJmsFactory.connection()).close();

        sender.close();
        sender.close();

        verify(eventProducer).close();
        verify(ScriptedJmsFactory.session()).close();
        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(sender.isDisconnected());
    }

    @Test
    public void closeReleasesSeparateProjectProducer() throws Exception {
        connect(settingsWithoutSelector());
        doThrow(new JMSException("producer")).when(projectProducer).close();

        sender.close();

        verify(eventProducer).close();
        verify(projectProducer).close();
        verify(ScriptedJmsFactory.connection()).close();
        assertTrue(sender.isDisconnected());
    }

    @Test
    public void onExceptionNotifiesConnectionFailure() throws Exception {
        connect(settings());
        List<Exception> causes = SenderFailureSinkProbe.attach(sender);
        JMSException failure = new JMSException("x");

        sender.onException(failure);

        assertEquals(1, causes.size());
        assertTrue(causes.get(0) instanceof NjamsSdkRuntimeException);
        assertSame(failure, causes.get(0).getCause());
    }

    private void connect(ClientSettings settings) {
        sender.init(settings);
        sender.connect();
    }

    private void expectConnectFailure() {
        sender.init(settings());
        try {
            sender.connect();
            fail("connect must fail");
        } catch (NjamsSdkRuntimeException expected) {
            assertEquals("Unable to connect", expected.getMessage());
        }
    }

    private void expectSendFailure(CommonMessage message, String expectedMessage) {
        try {
            if (message instanceof LogMessage) {
                sender.send((LogMessage) message, SESSION_ID);
            } else if (message instanceof ProjectMessage) {
                sender.send((ProjectMessage) message, SESSION_ID);
            } else {
                sender.send((TraceMessage) message, SESSION_ID);
            }
            fail("send must fail");
        } catch (NjamsSdkRuntimeException expected) {
            assertEquals(expectedMessage, expected.getMessage());
        }
    }

    private static List<Message> sentBy(MessageProducer producer) throws JMSException {
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(producer, atLeastOnce()).send(sent.capture());
        return sent.getAllValues();
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

    /** Removes the quick-retry waits so a failing send fails at once. */
    private static final class NoDelayJmsSender extends JmsSender {
        @Override
        protected long[] getSmoothingDelaysMs() {
            return new long[0];
        }
    }
}
