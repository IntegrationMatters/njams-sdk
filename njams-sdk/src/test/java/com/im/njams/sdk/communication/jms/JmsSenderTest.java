/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
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
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * This class tests if the JmsSender works correctly.
 *
 * @author krautenberg@integrationmatters.com
 * @version 4.0.5
 */
public class JmsSenderTest {

    private static final LocalDateTime JOBSTART = LocalDateTime.of(2018, 11, 20, 14, 55, 34, 555000000);
    private static final LocalDateTime BUSINESSSTART = LocalDateTime.of(2018, 11, 20, 14, 57, 55, 240000000);
    private static final LocalDateTime BUSINESSEND = LocalDateTime.of(2018, 11, 20, 14, 58, 12, 142000000);
    private static final LocalDateTime JOBEND = LocalDateTime.of(2018, 11, 20, 14, 59, 58, 856000000);
    private static final LocalDateTime SENTAT = LocalDateTime.of(2018, 11, 20, 15, 00, 01, 213000000);

    private static final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
    private JmsSender sender = null;

    @Before
    public void beforeEach() {
        WorkingJmsFactory.reset();
        sender = spy(new JmsSender() {
            @Override
            String serialize(CommonMessage msg) {
                return "dummy data";
            }
        });

        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        sender.init(ClientSettings.from(props));
    }

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
    public void queueFullIsSmoothedThenRetriedIndefinitelyUnderNonePolicy() throws Exception {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        JmsSender noneSender = spy(new JmsSender() {
            @Override
            String serialize(CommonMessage msg) {
                return "dummy data";
            }

            @Override
            protected long[] getSmoothingDelaysMs() {
                return new long[] { 0, 0, 0 };
            }

            @Override
            protected long getCongestionRetryDelayMs() {
                return 0;
            }
        });
        noneSender.init(ClientSettings.from(props));
        final MessageProducer producer = noneSender.eventProducer = mock(MessageProducer.class);
        final Session session = noneSender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        // more failures than the smoothing window: proves congestion is retried past it, never discarded
        final int[] attempt = { 0 };
        doAnswer(invocation -> {
            if (++attempt[0] <= 10) {
                throw new ResourceAllocationException("Queue limit exceeded");
            }
            return null;
        }).when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        noneSender.sendMessage(producer, msg, "messageType", null);
        verify(producer, times(11)).send(any());
    }

    @Test
    public void queueFullUnderDiscardPolicyGivesUpOnTheFirstAttempt() throws Exception {
        final MessageProducer producer = sender.eventProducer = mock(MessageProducer.class);
        final Session session = sender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        doThrow(new ResourceAllocationException("Queue limit exceeded")).when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        try {
            sender.sendMessage(producer, msg, "messageType", null);
            fail("expected the congestion failure to be reported under the discard policy");
        } catch (ResourceAllocationException expected) {
            // expected: the SDK drops the message without retiring the connection
        }
        verify(producer, times(1)).send(any());
    }

    @Test
    public void queueFullUnderConnectionLossPolicyRetriesInsteadOfDiscardingImmediately() throws Exception {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        props.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "onconnectionloss");
        JmsSender onConnectionLossSender = spy(new JmsSender() {
            @Override
            String serialize(CommonMessage msg) {
                return "dummy data";
            }
        });
        onConnectionLossSender.init(ClientSettings.from(props));
        final MessageProducer producer = onConnectionLossSender.eventProducer = mock(MessageProducer.class);
        final Session session = onConnectionLossSender.session = mock(Session.class);
        when(session.createTextMessage(any())).thenReturn(mock(TextMessage.class));
        ResourceAllocationException er1 = new ResourceAllocationException("Queue limit exceeded");
        ResourceAllocationException er2 = new ResourceAllocationException("Queue limit exceeded");
        doThrow(er1).doThrow(er2).doNothing().when(producer).send(any());
        final CommonMessage msg = mock(CommonMessage.class);
        when(msg.getPath()).thenReturn("path");
        onConnectionLossSender.sendMessage(producer, msg, "messageType", null);
        verify(producer, times(3)).send(any());
    }

    @Test
    public void connectUsesDestinationPrefixAlternativeKey() {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        props.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, WorkingJmsFactory.NAME);
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX, "njams.alt");
        JmsSender jmsSender = new JmsSender();
        jmsSender.init(ClientSettings.from(props));
        jmsSender.connect();
        assertEquals(List.of("njams.alt.event"), WorkingJmsFactory.getCreatedQueueNames());
    }

    @Test
    public void connectPrefersPrimaryDestinationKeyOverAlternative() {
        Properties props = new Properties();
        props.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        props.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, WorkingJmsFactory.NAME);
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams.primary");
        props.put(NjamsSettings.PROPERTY_JMS_DESTINATION_PREFIX, "njams.alt");
        JmsSender jmsSender = new JmsSender();
        jmsSender.init(ClientSettings.from(props));
        jmsSender.connect();
        assertEquals(List.of("njams.primary.event"), WorkingJmsFactory.getCreatedQueueNames());
    }

    @Test
    public void isCongestionTrueForResourceAllocationException() {
        assertTrue(sender.isCongestion(new ResourceAllocationException("full")));
    }

    @Test
    public void isCongestionTrueWhenResourceAllocationExceptionIsNested() {
        assertTrue(sender.isCongestion(
            new NjamsSdkRuntimeException("Unable to send LogMessage", new ResourceAllocationException("full"))));
    }

    @Test
    public void isCongestionFalseForOtherJmsException() {
        assertFalse(sender.isCongestion(new InvalidDestinationException("bad destination")));
    }

    @Test
    public void isCongestionFalseForUnrecognisedFailure() {
        assertFalse(sender.isCongestion(new RuntimeException("socket closed")));
    }

    @Test
    public void isCongestionFalseForNull() {
        assertFalse(sender.isCongestion(null));
    }
}
