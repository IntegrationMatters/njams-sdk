/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import org.junit.Test;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.LocalDateTime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * This class tests if the JmsSender works correctly.
 */
public class JmsSenderTest {

    private static final LocalDateTime JOBSTART = LocalDateTime.of(2018, 11, 20, 14, 55, 34, 555000000);
    private static final LocalDateTime BUSINESSSTART = LocalDateTime.of(2018, 11, 20, 14, 57, 55, 240000000);
    private static final LocalDateTime BUSINESSEND = LocalDateTime.of(2018, 11, 20, 14, 58, 12, 142000000);
    private static final LocalDateTime JOBEND = LocalDateTime.of(2018, 11, 20, 14, 59, 58, 856000000);
    private static final LocalDateTime SENTAT = LocalDateTime.of(2018, 11, 20, 15, 00, 01, 213000000);

    private static final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    /**
     * The serializer should use ISO 8601 for serializing LocalDateTime.
     * Supported from the Server are
     * YYYY-MM-DDThh:mm:ss.sss and
     * YYYY-MM-DDThh:mm:ss.sssZ
     */
    @Test
    public void LocalDateTimeSerializerTest() {
        LogMessage message = new LogMessage();
        message.setJobStart(JOBSTART);
        message.setBusinessStart(BUSINESSSTART);
        message.setBusinessEnd(BUSINESSEND);
        message.setJobEnd(JOBEND);
        message.setSentAt(SENTAT);

        try {
            String data = mapper.writeValueAsString(message);
            assertTrue(data.contains("\"sentAt\" : \"2018-11-20T15:00:01.213\""));
            assertTrue(data.contains("\"jobStart\" : \"2018-11-20T14:55:34.555\""));
            assertTrue(data.contains("\"jobEnd\" : \"2018-11-20T14:59:58.856\""));
            assertTrue(data.contains("\"businessStart\" : \"2018-11-20T14:57:55.240\""));
            assertTrue(data.contains("\"businessEnd\" : \"2018-11-20T14:58:12.142\""));
        } catch (JsonProcessingException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void queueIsFullTest() throws JMSException, InterruptedException {
        final String ERROR_MESSAGE = "Queue limit exceeded";
        final JmsSender sender = spy(new JmsSender());
        final MessageProducer producer = sender.producer = mock(MessageProducer.class);
        final Session session = sender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        ResourceAllocationException er1 = new ResourceAllocationException(ERROR_MESSAGE);
        ResourceAllocationException er2 = new ResourceAllocationException(ERROR_MESSAGE);
        doThrow(er1).doThrow(er2).doNothing().when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        sender.sendMessage(msg, "messageType", "data");
        verify(producer, times(3)).send(any());
    }

    @Test(expected = ResourceAllocationException.class)
    public void queueIsFullMaxTriesTest() throws JMSException, InterruptedException {
        final String ERROR_MESSAGE = "Queue limit exceeded";
        final JmsSender sender = spy(new JmsSender());
        final int maxTries = 1;
        sender.setMaxTries(maxTries);
        sender.setExceptionIdleTimeInMilliseconds(1);
        final MessageProducer producer = sender.producer = mock(MessageProducer.class);
        final Session session = sender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        ResourceAllocationException er = new ResourceAllocationException(ERROR_MESSAGE);
        doThrow(er).when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        try {
            sender.sendMessage(msg, "messageType", "data");
        }catch(ResourceAllocationException ex){
            throw ex;
        }finally{
            verify(producer, times(maxTries)).send(any());
        }
    }
}
