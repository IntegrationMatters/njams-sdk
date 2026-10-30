package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;

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
     * A JNDI provider that resolves without a broker and hands out a {@code jakarta.jms} connection factory.
     * The javax twin uses ActiveMQ's client-side initial context factory for this; there is no jakarta broker
     * client on this module's test classpath (see the jakarta namespace module), so the provider is supplied
     * here instead. It opens no socket either.
     */
    public static class JakartaInitialContextFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            Context context = mock(Context.class);
            when(context.lookup("ConnectionFactory")).thenReturn(mock(ConnectionFactory.class));
            return context;
        }
    }

    /**
     * Settings whose JNDI lookup resolves without a broker: the initial context factory above synthesises the
     * ConnectionFactory and opens no socket.
     */
    private static ClientSettings resolvableSettings() {
        Map<String, String> map = new HashMap<>();
        map.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY, JakartaInitialContextFactory.class.getName());
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
