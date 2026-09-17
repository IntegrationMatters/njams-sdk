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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Test-only {@link JmsFactory} that fully mocks the JMS API chain (connection, session, producers), so a
 * {@code connect()} call completes successfully, and records the queue names it was asked to create. Used
 * to verify which destination name a sender/receiver resolves without needing a real JMS provider.
 */
public class WorkingJmsFactory implements JmsFactory {

    public static final String NAME = "WorkingJmsFactory";

    private static final List<String> CREATED_QUEUE_NAMES = Collections.synchronizedList(new ArrayList<>());

    public static void reset() {
        CREATED_QUEUE_NAMES.clear();
    }

    public static List<String> getCreatedQueueNames() {
        return new ArrayList<>(CREATED_QUEUE_NAMES);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ClientSettings settings) {
        // nothing to do
    }

    @Override
    public ConnectionFactory createConnectionFactory() throws JMSException {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createProducer(any())).thenReturn(mock(MessageProducer.class));
        return connectionFactory;
    }

    @Override
    public Queue createQueue(Session session, String queueName) {
        CREATED_QUEUE_NAMES.add(queueName);
        return mock(Queue.class);
    }
}
