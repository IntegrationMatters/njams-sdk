package com.im.njams.sdk.communication.jms.javax.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link JndiJmsFactory}: name, not-initialized guard, JNDI-less topic/queue
 * delegation and idempotent init.
 */
public class JndiJmsFactoryTest {

    private static ClientSettings emptySettings() {
        return ClientSettings.from(new HashMap<>());
    }

    /**
     * Settings whose JNDI lookup resolves without a broker: ActiveMQ's client-side initial context factory
     * synthesises the ConnectionFactory from the URL and opens no socket.
     */
    private static ClientSettings resolvableSettings() {
        Map<String, String> map = new HashMap<>();
        map.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY,
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        map.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "tcp://localhost:61616");
        map.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "ConnectionFactory");
        return ClientSettings.from(map);
    }

    @Test
    public void nameAndToString() {
        JndiJmsFactory factory = new JndiJmsFactory();
        assertEquals(JndiJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(JndiJmsFactory.NAME));
    }

    @Test
    public void createConnectionFactoryBeforeInitThrows() {
        try {
            new JndiJmsFactory().createConnectionFactory();
            fail("expected IllegalStateException when not initialized");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void createTopicWithoutContextDelegatesToSession() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        Session session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(session.createTopic("t")).thenReturn(topic);

        // no JNDI context (not initialized) -> falls back to the session
        assertSame(topic, factory.createTopic(session, "t"));
    }

    @Test
    public void createQueueWithoutContextDelegatesToSession() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createQueue("q")).thenReturn(queue);

        assertSame(queue, factory.createQueue(session, "q"));
    }

    @Test
    public void closeWithoutContextIsNoOp() {
        // must not throw even though it was never initialized
        new JndiJmsFactory().close();
    }

    @Test
    public void initWithoutConnectionFactoryThrowsNamingTheSetting() {
        JndiJmsFactory factory = new JndiJmsFactory();
        try {
            factory.init(emptySettings());
            fail("expected NamingException; JNDI has nothing to look up");
        } catch (Exception e) {
            assertEquals(NamingException.class, e.getClass());
            assertTrue(e.getMessage(), e.getMessage().contains(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY));
        }
    }

    @Test
    public void initIsIdempotent() throws Exception {
        JndiJmsFactory factory = new JndiJmsFactory();
        factory.init(resolvableSettings());
        // second call must return early without error
        factory.init(resolvableSettings());
    }
}
