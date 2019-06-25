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
package com.im.njams.sdk.communication.jms.connector;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.jms.JmsConstants;
import org.junit.Before;
import org.junit.Test;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JmsReceiverConnectorTest {

    public static final String TEST_TOPIC_NAME = "TestCommandsDestination";

    public static final String TEST_NAME = "TestJmsReceiverConnector";

    private JmsReceiverConnector connector;

    @Before
    public void setUpConnector(){
        Properties properties = mock(Properties.class);
        when(properties.containsKey(JmsConstants.COMMANDS_DESTINATION)).thenReturn(true);
        when(properties.getProperty(JmsConstants.COMMANDS_DESTINATION)).thenReturn(TEST_TOPIC_NAME);
        Njams njams = mock(Njams.class);
        Path clientPath = mock(Path.class);
        when(njams.getClientPath()).thenReturn(clientPath);
        when(clientPath.toString()).thenReturn(">First>Second>Third>");
        MessageListener messageListener = mock(MessageListener.class);
        connector = spy(new JmsReceiverConnector(properties, TEST_NAME, messageListener, njams));
    }

    @Test
    public void extConnect() throws JMSException, NamingException {
        doReturn(null).when(connector).getOrCreateTopic();
        doReturn(null).when(connector).createConsumer(any());
        connector.extConnect();
        verify(connector).getOrCreateTopic();
        verify(connector).createConsumer(any());
    }

    @Test
    public void getTopic() throws JMSException, NamingException {
        InitialContext context = connector.context = mock(InitialContext.class);
        Session session = connector.session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(context.lookup(TEST_TOPIC_NAME)).thenReturn(topic);
        assertEquals(topic, connector.getOrCreateTopic());
        verify(context).lookup(TEST_TOPIC_NAME);
        verify(session, times(0)).createTopic(any());
    }

    @Test
    public void createTopic() throws JMSException, NamingException {
        InitialContext context = connector.context = mock(InitialContext.class);
        Session session = connector.session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(context.lookup(TEST_TOPIC_NAME)).thenThrow(new NameNotFoundException(TEST_TOPIC_NAME));
        when(session.createTopic(TEST_TOPIC_NAME)).thenReturn(topic);
        assertEquals(topic, connector.getOrCreateTopic());
        verify(context).lookup(TEST_TOPIC_NAME);
        verify(session).createTopic(any());
    }

    @Test
    public void createConsumer() throws JMSException {
        Session session = connector.session = mock(Session.class);
        Topic topic = mock(Topic.class);
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(session.createConsumer(eq(topic), any())).thenReturn(consumer);

        assertEquals(consumer, connector.createConsumer(topic));
        verify(consumer).setMessageListener(any());
    }

    @Test
    public void extClose() throws JMSException {
        MessageConsumer consumer = connector.consumer = mock(MessageConsumer.class);
        List<Exception> exceptions = connector.extClose();
        assertTrue(exceptions.isEmpty());
        verifyClose(consumer);
        verifyNull();
    }

    private void verifyClose(MessageConsumer consumer) throws JMSException {
        verify(consumer).close();
    }

    private void verifyNull() {
        assertNull(connector.consumer);
    }

    @Test
    public void extCloseWithExceptions() throws JMSException {
        MessageConsumer consumer = connector.consumer = mock(MessageConsumer.class);
        doThrow(new JMSException("ConsumerTest")).when(consumer).close();
        List<Exception> exceptions = connector.extClose();
        assertFalse(exceptions.isEmpty());
        assertTrue(exceptions.size() == 1);
        verifyClose(consumer);
        verifyNull();
    }

    @Test
    public void extLibrariesToCheck() {
        Set<String> libs = connector.extLibrariesToCheck();
        Set<String> libsToCheck = new HashSet<>();

        libsToCheck.add("javax.jms.JMSException");
        libsToCheck.add("javax.jms.MessageConsumer");
        libsToCheck.add("javax.jms.MessageListener");
        libsToCheck.add("javax.jms.Topic");
        libsToCheck.add("javax.naming.NameNotFoundException");
        libsToCheck.add("javax.naming.NamingException");

        libs.stream().forEach(lib -> assertTrue(libsToCheck.contains(lib)));
        libsToCheck.stream().forEach(lib -> assertTrue(libs.contains(lib)));
    }

    @Test
    public void getTopicName() {
    }
}