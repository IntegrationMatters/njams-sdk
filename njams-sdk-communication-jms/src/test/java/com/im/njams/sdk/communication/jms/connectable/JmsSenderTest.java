/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.communication.connectable.sender.Sender;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.communication.jms.connector.JmsSenderConnector;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class JmsSenderTest {

    private JmsSender jmsSender;

    @Before
    public void setUp() {
        jmsSender = spy(new JmsSender());
    }

    //initialize
    @Test
    public void initialize() {
        Properties properties = new Properties();
        jmsSender.initialize(properties);
    }

    //send
    @Test
    public void sendLogMessage() throws JsonProcessingException, JMSException {
        final LogMessage mockedLogMessage = mock(LogMessage.class);
        final String logMessageType = Sender.NJAMS_MESSAGETYPE_EVENT;

        mockSend(mockedLogMessage, logMessageType);
        jmsSender.send(mockedLogMessage);
        verifySending(mockedLogMessage, logMessageType);
    }

    private <T extends CommonMessage> void mockSend(T mockedMessage, String messageType)
            throws JsonProcessingException, JMSException {
        doReturn(messageType).when(jmsSender).serializeMessageToJson(mockedMessage);
        doNothing().when(jmsSender).sendMessage(eq(mockedMessage), eq(messageType), any());
        doNothing().when(jmsSender).logSentMessage(eq(mockedMessage), any());
    }

    private <T extends CommonMessage> void verifySending(T mockedMessage, String messageType)
            throws JsonProcessingException, JMSException {
        verify(jmsSender).serializeMessageToJson(mockedMessage);
        verify(jmsSender).sendMessage(eq(mockedMessage), eq(messageType), any());
        verify(jmsSender).logSentMessage(eq(mockedMessage), any());
    }

    @Test
    public void sendProjectMessage() throws JsonProcessingException, JMSException {
        final ProjectMessage mockedProjectMessage = mock(ProjectMessage.class);
        final String projectMessageType = Sender.NJAMS_MESSAGETYPE_PROJECT;

        mockSend(mockedProjectMessage, projectMessageType);
        jmsSender.send(mockedProjectMessage);
        verifySending(mockedProjectMessage, projectMessageType);
    }

    @Test
    public void sendTraceMessage() throws JsonProcessingException, JMSException {
        final TraceMessage mockedTraceMessage = mock(TraceMessage.class);
        final String traceMessageType = Sender.NJAMS_MESSAGETYPE_TRACE;

        mockSend(mockedTraceMessage, traceMessageType);
        jmsSender.send(mockedTraceMessage);
        verifySending(mockedTraceMessage, traceMessageType);
    }

    //getName
    @Test
    public void getName() {
        assertEquals(JmsConstants.COMMUNICATION_NAME, jmsSender.getName());
    }

    //serializeMessageToJson

    @Test
    public void serializeLogMessageToJson() throws IOException {
        fakeInitialize();
        LogMessage msg = new LogMessage();
        msg.setMachineName("Test");
        String serializedMessage = jmsSender.serializeMessageToJson(msg);
        LogMessage parsedMessage = JsonUtils.parse(serializedMessage, LogMessage.class);
        assertEquals(msg.getMachineName(), parsedMessage.getMachineName());
        assertNotEquals(msg, parsedMessage);
    }

    @Test
    public void serializeProjectMessageToJson() throws IOException {
        fakeInitialize();
        ProjectMessage msg = new ProjectMessage();
        msg.setMachine("Test");
        String serializedMessage = jmsSender.serializeMessageToJson(msg);
        ProjectMessage parsedMessage = JsonUtils.parse(serializedMessage, ProjectMessage.class);
        assertEquals(msg.getMachine(), parsedMessage.getMachine());
        assertNotEquals(msg, parsedMessage);
    }

    @Test
    public void serializeTraceMessageToJson() throws IOException {
        fakeInitialize();
        TraceMessage msg = new TraceMessage();
        msg.setClientVersion("Test");
        String serializedMessage = jmsSender.serializeMessageToJson(msg);
        ProjectMessage parsedMessage = JsonUtils.parse(serializedMessage, ProjectMessage.class);
        assertEquals(msg.getClientVersion(), parsedMessage.getClientVersion());
        assertNotEquals(msg, parsedMessage);
    }

    private void fakeInitialize() {
        JmsSenderConnector mockedConnector = mock(JmsSenderConnector.class);
        doReturn(mockedConnector).when(jmsSender).initialize(any());
        jmsSender.init(new Properties());
    }

    //sendMessage
    @Test
    public void tryToSendLogMessage() throws JMSException {
        final String data = "Test";
        TextMessage mockedTextMessage = mockForTryToSend(data);

        final LogMessage mockedMessage = mock(LogMessage.class);
        final String messageType = Sender.NJAMS_MESSAGETYPE_EVENT;

        final String path = "TestPath";
        when(mockedMessage.getPath()).thenReturn(path);
        final String logId = "TestLogId";
        when(mockedMessage.getLogId()).thenReturn(logId);

        jmsSender.sendMessage(mockedMessage, messageType, data);

        verifyMockedTextMessage(mockedTextMessage, logId, messageType, path);
    }

    private TextMessage mockForTryToSend(String data) throws JMSException {
        fakeInitialize();
        final JmsSenderConnector mockedConnector = (JmsSenderConnector) jmsSender.getConnector();
        final Session mockedSession = mock(Session.class);
        when(mockedConnector.getSession()).thenReturn(mockedSession);

        final TextMessage mockedTextMessage = mock(TextMessage.class);
        when(mockedSession.createTextMessage(data)).thenReturn(mockedTextMessage);

        final MessageProducer producerMock = mock(MessageProducer.class);
        when(mockedConnector.getProducer()).thenReturn(producerMock);
        return mockedTextMessage;
    }

    private void verifyMockedTextMessage(TextMessage mockedTextMessage, String logId, String messageType, String path)
            throws JMSException {
        verify(mockedTextMessage).setStringProperty(Sender.NJAMS_SERVER_LOGID, logId);
        verifyMockedTextMessage(mockedTextMessage, messageType, path);
    }

    private void verifyMockedTextMessage(TextMessage mockedTextMessage, String messageType, String path)
            throws JMSException {
        verify(mockedTextMessage).setStringProperty(Sender.NJAMS_SERVER_MESSAGEVERSION, MessageVersion.V4.toString());
        verify(mockedTextMessage).setStringProperty(Sender.NJAMS_SERVER_MESSAGETYPE, messageType);
        verify(mockedTextMessage).setStringProperty(Sender.NJAMS_SERVER_PATH, path);
        MessageProducer producerMock = ((JmsSenderConnector) jmsSender.getConnector()).getProducer();
        verify(producerMock).send(mockedTextMessage);
    }

    @Test
    public void tryToSendProjectMessage() throws JMSException {
        final String data = "Test";
        TextMessage mockedTextMessage = mockForTryToSend(data);

        final ProjectMessage mockedMessage = mock(ProjectMessage.class);
        final String messageType = Sender.NJAMS_MESSAGETYPE_PROJECT;

        final String path = "TestPath";
        when(mockedMessage.getPath()).thenReturn(path);

        jmsSender.sendMessage(mockedMessage, messageType, data);

        verifyMockedTextMessage(mockedTextMessage, messageType, path);
        verify(mockedTextMessage, times(0)).setStringProperty(eq(Sender.NJAMS_SERVER_LOGID), any());
    }

    @Test
    public void tryToSendTraceMessage() throws JMSException {
        final String data = "Test";
        TextMessage mockedTextMessage = mockForTryToSend(data);

        final TraceMessage mockedMessage = mock(TraceMessage.class);
        final String messageType = Sender.NJAMS_MESSAGETYPE_TRACE;

        final String path = "TestPath";
        when(mockedMessage.getPath()).thenReturn(path);

        jmsSender.sendMessage(mockedMessage, messageType, data);

        verifyMockedTextMessage(mockedTextMessage, messageType, path);
        verify(mockedTextMessage, times(0)).setStringProperty(eq(Sender.NJAMS_SERVER_LOGID), any());
    }

    //logSentMessage

    @Test
    public void showSentLogMessageExample() throws JMSException {
        fakeInitialize();
        final LogMessage msg = new LogMessage();
        msg.setPath(">TestPath>");
        JmsSenderConnector mockedConnector = (JmsSenderConnector) jmsSender.getConnector();
        MessageProducer mockedProducer = mock(MessageProducer.class);
        when(mockedConnector.getProducer()).thenReturn(mockedProducer);
        Destination mockedDestination = mock(Destination.class);
        when(mockedProducer.getDestination()).thenReturn(mockedDestination);
        when(mockedDestination.toString()).thenReturn("TestDestination");
        final String data = "TestData";
        jmsSender.logSentMessage(msg, data);
    }
}