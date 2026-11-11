package com.im.njams.sdk.communication.jms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import com.im.njams.sdk.settings.ClientSettings;

/**
 * Test-only {@link JmsFactory} whose whole JMS object chain consists of shared Mockito mocks, so a test can
 * drive a connect, inspect what the SDK did with each object, and re-stub single mocks to inject failures.
 * Call {@link #reset()} before each test.
 */
public class ScriptedJmsFactory implements JmsFactory {

    public static final String NAME = "ScriptedJmsFactory";

    private static ConnectionFactory connectionFactory;
    private static Connection connection;
    private static Session session;
    private static MessageConsumer consumer;
    private static MessageProducer producer;
    private static Topic topic;
    private static Queue queue;
    private static final List<String> QUEUE_NAMES = new CopyOnWriteArrayList<>();

    /** Creates a fresh, fully stubbed mock chain. */
    public static synchronized void reset() {
        connectionFactory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        session = mock(Session.class);
        consumer = mock(MessageConsumer.class);
        producer = mock(MessageProducer.class);
        topic = mock(Topic.class);
        queue = mock(Queue.class);
        QUEUE_NAMES.clear();
        try {
            when(connectionFactory.createConnection()).thenReturn(connection);
            when(connectionFactory.createConnection(anyString(), anyString())).thenReturn(connection);
            when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
            when(session.createConsumer(any())).thenReturn(consumer);
            when(session.createConsumer(any(), anyString())).thenReturn(consumer);
            when(session.createProducer(any())).thenReturn(producer);
            when(session.createTextMessage(anyString())).thenAnswer(i -> mock(TextMessage.class));
        } catch (JMSException e) {
            throw new IllegalStateException(e);
        }
    }

    public static synchronized ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    public static synchronized Connection connection() {
        return connection;
    }

    public static synchronized Session session() {
        return session;
    }

    public static synchronized MessageConsumer consumer() {
        return consumer;
    }

    public static synchronized MessageProducer producer() {
        return producer;
    }

    public static synchronized Topic topic() {
        return topic;
    }

    public static synchronized Queue queue() {
        return queue;
    }

    /** @return the names {@link #createQueue(Session, String)} was called with, in order. */
    public static List<String> queueNames() {
        return new ArrayList<>(QUEUE_NAMES);
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
    public ConnectionFactory createConnectionFactory() {
        return connectionFactory();
    }

    @Override
    public Topic createTopic(Session session, String topicName) {
        return topic();
    }

    @Override
    public Queue createQueue(Session session, String queueName) {
        QUEUE_NAMES.add(queueName);
        return queue();
    }
}
