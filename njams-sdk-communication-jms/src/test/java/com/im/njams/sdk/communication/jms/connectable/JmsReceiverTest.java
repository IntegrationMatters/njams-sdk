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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.jms.JmsConstants;
import com.im.njams.sdk.communication.jms.connector.JmsReceiverConnector;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class JmsReceiverTest {

    private JmsReceiver jmsReceiver;

    @Before
    public void setUp() {
        jmsReceiver = spy(new JmsReceiver());
    }

    //GetName
    @Test
    public void getName() {
        assertEquals(JmsConstants.COMMUNICATION_NAME, jmsReceiver.getName());
    }

    //OnMessage
    @Test
    public void onMessageThrowsException() throws JMSException {
        Message mockedMsg = mock(Message.class);
        JMSException ex = new JMSException("EXCEPTION");
        doThrow(ex).when(mockedMsg).getStringProperty(JmsReceiver.NJAMS_CONTENT);

        jmsReceiver.onMessage(mockedMsg);

        verifyFailureOnMessage();
    }

    private void verifyFailureOnMessage() {
        verify(jmsReceiver, times(0)).onInstruction(any());
        verify(jmsReceiver, times(0)).reply(any(), any());
    }

    @Test
    public void onMessageWithoutNjamsContent() throws JMSException {
        Message mockedMsg = mock(Message.class);
        when(mockedMsg.getStringProperty(JmsReceiver.NJAMS_CONTENT)).thenReturn(null);

        jmsReceiver.onMessage(mockedMsg);

        verifyFailureOnMessage();
    }

    @Test
    public void onMessageWithEmptyNjamsContent() throws JMSException {
        Message mockedMsg = mock(Message.class);
        when(mockedMsg.getStringProperty(JmsReceiver.NJAMS_CONTENT)).thenReturn("");

        jmsReceiver.onMessage(mockedMsg);

        verifyFailureOnMessage();
    }

    @Test
    public void onMessageWithoutMessageFormatJson() throws JMSException {
        Message mockedMsg = mock(Message.class);
        when(mockedMsg.getStringProperty(JmsReceiver.NJAMS_CONTENT)).thenReturn("XML");

        jmsReceiver.onMessage(mockedMsg);

        verifyFailureOnMessage();
    }

    @Test
    public void onMessageWithoutInstruction() throws JMSException {
        Message mockedMsg = mock(Message.class);
        when(mockedMsg.getStringProperty(JmsReceiver.NJAMS_CONTENT)).thenReturn(JmsReceiver.MESSAGE_FORMAT_JSON);

        doReturn(null).when(jmsReceiver).getInstruction(mockedMsg);
        jmsReceiver.onMessage(mockedMsg);

        verifyFailureOnMessage();
    }

    @Test
    public void onMessage() throws JMSException {
        Message mockedMsg = mock(Message.class);
        when(mockedMsg.getStringProperty(JmsReceiver.NJAMS_CONTENT)).thenReturn(JmsReceiver.MESSAGE_FORMAT_JSON);

        Instruction instruction = mock(Instruction.class);
        doNothing().when(jmsReceiver).onInstruction(instruction);
        doNothing().when(jmsReceiver).reply(mockedMsg, instruction);
        doReturn(instruction).when(jmsReceiver).getInstruction(mockedMsg);
        jmsReceiver.onMessage(mockedMsg);

        verifySuccessOnMessage(mockedMsg, instruction);
    }

    private void verifySuccessOnMessage(Message msg, Instruction instruction) {
        verify(jmsReceiver, times(1)).reply(msg, instruction);
        verify(jmsReceiver, times(1)).onInstruction(instruction);
    }

    //getInstruction

    @Test
    public void getInstructionWithWrongMessageClass() {
        Message mockedMsg = mock(Message.class);
        Instruction instruction = jmsReceiver.getInstruction(mockedMsg);
        assertNull(instruction);
    }

    @Test
    public void getInstructionWithNoRequest() throws Exception {
        fakeInitialize();
        TextMessage mockedMsg = mock(TextMessage.class);
        Instruction instructionBeforeSerialization = new Instruction();
        when(mockedMsg.getText()).thenReturn(JsonUtils.serialize(instructionBeforeSerialization));

        Instruction instructionAfterSerialization = jmsReceiver.getInstruction(mockedMsg);
        assertNull(instructionAfterSerialization);
    }

    @Test
    public void getInstructionFromMessage() throws Exception {
        fakeInitialize();
        TextMessage mockedMsg = mock(TextMessage.class);
        Instruction instructionBeforeSerialization = new Instruction();
        Request request = new Request();
        final String TEST_COMMAND = "TestCommand";
        request.setCommand(TEST_COMMAND);
        instructionBeforeSerialization.setRequest(request);
        when(mockedMsg.getText()).thenReturn(JsonUtils.serialize(instructionBeforeSerialization));

        Instruction instructionAfterSerialization = jmsReceiver.getInstruction(mockedMsg);
        assertEquals(TEST_COMMAND, instructionAfterSerialization.getCommand());
    }

    private void fakeInitialize() {
        JmsReceiverConnector mockedConnector = mock(JmsReceiverConnector.class);
        doReturn(mockedConnector).when(jmsReceiver).initialize(any());
        jmsReceiver.init(null);
    }

    //reply

    @Test
    public void reply() throws JMSException {
        fakeInitialize();

        //createReplier start
        final JmsReceiverConnector mockedConnector = (JmsReceiverConnector) jmsReceiver.getConnector();
        Session mockedSession = mock(Session.class);
        when(mockedConnector.getSession()).thenReturn(mockedSession);

        TextMessage incomingMessage = mock(TextMessage.class);
        Destination mockedDestination = mock(Destination.class);
        when(incomingMessage.getJMSReplyTo()).thenReturn(mockedDestination);

        MessageProducer mockedProcuder = mock(MessageProducer.class);
        when(mockedSession.createProducer(mockedDestination)).thenReturn(mockedProcuder);
        //createReplier end

        //serializeResponse start
        Instruction instructionToSerialize = new Instruction();
        Request request = new Request();
        final String TEST_COMMAND = "TestCommand";
        request.setCommand(TEST_COMMAND);
        instructionToSerialize.setRequest(request);

        Response response = new Response();
        final String TEST_RESPONSE = "TestResponse";
        response.setResultMessage(TEST_RESPONSE);
        instructionToSerialize.setResponse(response);
        //serializeResponse end

        //wrapResponseInTextMessage start
        TextMessage outgoingMessage = mock(TextMessage.class);
        when(mockedSession.createTextMessage()).thenReturn(outgoingMessage);
        //wrapResponseInTextMessage end

        //addCorrelationIdIfPresent start
        final String TEST_CORRELATION_ID = "TestCorrelationId";
        when(incomingMessage.getJMSCorrelationID()).thenReturn(TEST_CORRELATION_ID);
        //addCorrelationIdIfPresent end

        jmsReceiver.reply(incomingMessage, instructionToSerialize);
        verify(outgoingMessage).setJMSCorrelationID(TEST_CORRELATION_ID);
        //sendTextMessage start
        verify(mockedProcuder).send(outgoingMessage);
        //sendTextMessage end

        //closeReplier start
        verify(mockedProcuder).close();
        //closeReplier end
    }

    //initialize
    @Test(expected = NullPointerException.class)
    public void initializeWithoutNjams() {
        Properties properties = new Properties();
        jmsReceiver.initialize(properties);
    }

    @Test
    public void initialize() {
        Njams njams = mock(Njams.class);
        Path path = new Path("Test");
        when(njams.getClientPath()).thenReturn(path);
        jmsReceiver.setNjams(njams);
        Properties properties = new Properties();
        jmsReceiver.initialize(properties);
    }
}