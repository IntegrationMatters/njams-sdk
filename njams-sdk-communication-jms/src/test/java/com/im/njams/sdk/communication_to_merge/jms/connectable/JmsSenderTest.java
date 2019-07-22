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
package com.im.njams.sdk.communication_to_merge.jms.connectable;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.common.MessageVersion;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.communication_to_merge.connectable.sender.Sender;
import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import com.im.njams.sdk.communication_to_merge.jms.connector.JmsSenderConnector;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.Assert.*;
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
    public void sendLogMessage() throws Exception {
        final LogMessage mockedLogMessage = mock(LogMessage.class);
        final String logMessageType = Sender.NJAMS_MESSAGETYPE_EVENT;

        mockSend(mockedLogMessage, logMessageType);
        jmsSender.send(mockedLogMessage);
        verifySending(mockedLogMessage, logMessageType);
    }

    private <T extends CommonMessage> void mockSend(T mockedMessage, String messageType) throws Exception {
        doReturn(messageType).when(jmsSender).serializeMessageToJson(mockedMessage);
        doNothing().when(jmsSender).sendMessage(eq(mockedMessage), eq(messageType), any());
        doNothing().when(jmsSender).logSentMessage(eq(mockedMessage), any());
    }

    private <T extends CommonMessage> void verifySending(T mockedMessage, String messageType) throws Exception {
        verify(jmsSender).serializeMessageToJson(mockedMessage);
        verify(jmsSender).sendMessage(eq(mockedMessage), eq(messageType), any());
        verify(jmsSender).logSentMessage(eq(mockedMessage), any());
    }

    @Test
    public void sendProjectMessage() throws Exception {
        final ProjectMessage mockedProjectMessage = mock(ProjectMessage.class);
        final String projectMessageType = Sender.NJAMS_MESSAGETYPE_PROJECT;

        mockSend(mockedProjectMessage, projectMessageType);
        jmsSender.send(mockedProjectMessage);
        verifySending(mockedProjectMessage, projectMessageType);
    }

    @Test
    public void sendTraceMessage() throws Exception {
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
    public void serializeLogMessageToJson() throws Exception {
        fakeInitialize();
        LogMessage msg = new LogMessage();
        msg.setMachineName("Test");
        String serializedMessage = jmsSender.serializeMessageToJson(msg);
        LogMessage parsedMessage = JsonUtils.parse(serializedMessage, LogMessage.class);
        assertEquals(msg.getMachineName(), parsedMessage.getMachineName());
        assertNotEquals(msg, parsedMessage);
    }

    @Test
    public void serializeProjectMessageToJson() throws Exception {
        fakeInitialize();
        ProjectMessage msg = new ProjectMessage();
        msg.setMachine("Test");
        String serializedMessage = jmsSender.serializeMessageToJson(msg);
        ProjectMessage parsedMessage = JsonUtils.parse(serializedMessage, ProjectMessage.class);
        assertEquals(msg.getMachine(), parsedMessage.getMachine());
        assertNotEquals(msg, parsedMessage);
    }

    @Test
    public void serializeTraceMessageToJson() throws Exception {
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

    /**
     * The serializer should use ISO 8601 for serializing LocalDateTime.
     * Supported from the Server are
     * YYYY-MM-DDThh:mm:ss.sss and
     * YYYY-MM-DDThh:mm:ss.sssZ
     */
    @Test
    public void LocalDateTimeSerializerTest() {
        final LocalDateTime JOBSTART = LocalDateTime.of(2018, 11, 20, 14, 55, 34, 555000000);
        final LocalDateTime BUSINESSSTART = LocalDateTime.of(2018, 11, 20, 14, 57, 55, 240000000);
        final LocalDateTime BUSINESSEND = LocalDateTime.of(2018, 11, 20, 14, 58, 12, 142000000);
        final LocalDateTime JOBEND = LocalDateTime.of(2018, 11, 20, 14, 59, 58, 856000000);
        final LocalDateTime SENTAT = LocalDateTime.of(2018, 11, 20, 15, 00, 01, 213000000);

        LogMessage message = new LogMessage();
        message.setJobStart(JOBSTART);
        message.setBusinessStart(BUSINESSSTART);
        message.setBusinessEnd(BUSINESSEND);
        message.setJobEnd(JOBEND);
        message.setSentAt(SENTAT);

        try {
            String data = JsonUtils.serialize(message);
            assertTrue(data.contains("\"sentAt\" : \"2018-11-20T15:00:01.213\""));
            assertTrue(data.contains("\"jobStart\" : \"2018-11-20T14:55:34.555\""));
            assertTrue(data.contains("\"jobEnd\" : \"2018-11-20T14:59:58.856\""));
            assertTrue(data.contains("\"businessStart\" : \"2018-11-20T14:57:55.240\""));
            assertTrue(data.contains("\"businessEnd\" : \"2018-11-20T14:58:12.142\""));
        } catch (Exception ex) {
            fail(ex.getMessage());
        }
    }
}