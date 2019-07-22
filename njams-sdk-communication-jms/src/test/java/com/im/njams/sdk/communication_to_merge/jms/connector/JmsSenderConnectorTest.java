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
package com.im.njams.sdk.communication_to_merge.jms.connector;

import com.im.njams.sdk.communication_to_merge.jms.JmsConstants;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JmsSenderConnectorTest {

    public static final String TEST_DESTINATION_NAME = "TestDestination.event";

    public static final String TEST_NAME = "TestJmsSenderConnector";

    private JmsSenderConnector connector;

    @Before
    public void setUpConnector(){
        Properties properties = mock(Properties.class);
        when(properties.getProperty(JmsConstants.DESTINATION)).thenReturn("TestDestination");
        connector = spy(new JmsSenderConnector(properties, TEST_NAME));
    }

    @Test
    public void extConnect() throws Exception {
        doReturn(null).when(connector).getOrCreateDestination(TEST_DESTINATION_NAME);
        doReturn(null).when(connector).createProducer(any());
        connector.extConnect();
        verify(connector).getOrCreateDestination(TEST_DESTINATION_NAME);
        verify(connector).createProducer(any());
    }

    @Test
    public void getDestination() throws NamingException, JMSException {
        InitialContext context = connector.context = mock(InitialContext.class);
        Session session = connector.session = mock(Session.class);
        Destination destination = mock(Destination.class);
        when(context.lookup(TEST_DESTINATION_NAME)).thenReturn(destination);
        assertEquals(destination, connector.getOrCreateDestination(TEST_DESTINATION_NAME));
        verify(context).lookup(TEST_DESTINATION_NAME);
        verify(session, times(0)).createQueue(any());
    }

    @Test
    public void createDestination() throws NamingException, JMSException {
        InitialContext context = connector.context = mock(InitialContext.class);
        Session session = connector.session = mock(Session.class);
        Queue destination = mock(Queue.class);
        when(context.lookup(TEST_DESTINATION_NAME)).thenThrow(new NameNotFoundException(TEST_DESTINATION_NAME));
        when(session.createQueue(TEST_DESTINATION_NAME)).thenReturn(destination);
        assertEquals(destination, connector.getOrCreateDestination(TEST_DESTINATION_NAME));
        verify(context).lookup(TEST_DESTINATION_NAME);
        verify(session).createQueue(any());
    }

    @Test
    public void createProducer() throws JMSException {
        Session session = connector.session = mock(Session.class);
        Destination destination = mock(Destination.class);
        MessageProducer producer = mock(MessageProducer.class);
        when(session.createProducer(eq(destination))).thenReturn(producer);

        assertEquals(producer, connector.createProducer(destination));
    }

    @Test
    public void extClose() throws JMSException {
        MessageProducer producer = connector.producer = mock(MessageProducer.class);
        List<Exception> exceptions = connector.extClose();
        assertTrue(exceptions.isEmpty());
        verifyClose(producer);
        verifyNull();
    }

    private void verifyClose(MessageProducer producer) throws JMSException {
        verify(producer).close();
    }

    private void verifyNull() {
        assertNull(connector.producer);
    }

    @Test
    public void extCloseWithExceptions() throws JMSException {
        MessageProducer producer = connector.producer = mock(MessageProducer.class);
        doThrow(new JMSException("ProducerTest")).when(producer).close();
        List<Exception> exceptions = connector.extClose();
        assertFalse(exceptions.isEmpty());
        assertTrue(exceptions.size() == 1);
        verifyClose(producer);
        verifyNull();
    }

    @Test
    public void extLibrariesToCheck() {
        Set<String> libs = connector.extLibrariesToCheck();
        Set<String> libsToCheck = new HashSet<>();

        libsToCheck.add("javax.jms.Destination");
        libsToCheck.add("javax.jms.JMSException");
        libsToCheck.add("javax.jms.MessageProducer");
        libsToCheck.add("javax.naming.NameNotFoundException");
        libsToCheck.add("javax.naming.NamingException");

        libs.stream().forEach(lib -> assertTrue(libsToCheck.contains(lib)));
        libsToCheck.stream().forEach(lib -> assertTrue(libs.contains(lib)));
    }

    @Test
    public void getProducer() {
        assertEquals(connector.producer, connector.getProducer());
    }
}