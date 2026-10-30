package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import javax.naming.NamingException;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for the SPI-based {@link JmsFactory#find} routing and the default interface methods.
 */
public class JmsFactoryTest {

    /** Minimal implementation used to exercise the interface default methods. */
    private static final class MinimalFactory implements JmsFactory {
        @Override
        public String getName() {
            return "minimal";
        }

        @Override
        public void init(ClientSettings settings) {
            // nothing
        }

        @Override
        public ConnectionFactory createConnectionFactory() {
            return null;
        }
    }

    private static ClientSettings settings(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return ClientSettings.from(m);
    }

    @Test
    public void findDefaultsToJndiWhenNothingMatches() {
        JmsFactory factory = JmsFactory.find(settings(), false);
        assertTrue(factory instanceof JndiJmsFactory);
    }

    @Test
    public void findByConnectionFactoryNameReturnsActiveMqSsl() {
        JmsFactory factory = JmsFactory.find(
                settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ActiveMqSslJmsFactory.NAME), false);
        assertTrue(factory instanceof ActiveMqSslJmsFactory);
    }

    @Test
    public void findByJmsFactoryKeyReturnsAzure() {
        JmsFactory factory = JmsFactory.find(
                settings(NjamsSettings.PROPERTY_JMS_JMSFACTORY, AzureServiceBusJmsFactory.NAME), false);
        assertTrue(factory instanceof AzureServiceBusJmsFactory);
    }

    @Test
    public void findBySimpleClassName() {
        JmsFactory factory = JmsFactory.find(
                settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, "JndiJmsFactory"), false);
        assertTrue(factory instanceof JndiJmsFactory);
    }

    @Test
    public void findByFullyQualifiedClassName() {
        JmsFactory factory = JmsFactory.find(
                settings(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY,
                        AzureServiceBusJmsFactory.class.getName()), false);
        assertTrue(factory instanceof AzureServiceBusJmsFactory);
    }

    @Test
    public void jmsFactoryKeyTakesPrecedenceOverConnectionFactory() {
        JmsFactory factory = JmsFactory.find(settings(
                NjamsSettings.PROPERTY_JMS_JMSFACTORY, AzureServiceBusJmsFactory.NAME,
                NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ActiveMqSslJmsFactory.NAME), false);
        assertTrue(factory instanceof AzureServiceBusJmsFactory);
    }

    @Test
    public void findWithoutConnectionFactoryFailsInitializingTheJndiDefault() {
        // JNDI is the default, but it has nothing to look up without a configured connection factory
        assertTrue(JmsFactory.find(settings(), false) instanceof JndiJmsFactory);
        try {
            JmsFactory.find(settings());
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(NamingException.class, e.getCause().getClass());
            assertTrue(e.getCause().getMessage(),
                e.getCause().getMessage().contains(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY));
        }
    }

    @Test
    public void defaultCreateTopicDelegatesToSession() throws Exception {
        MinimalFactory factory = new MinimalFactory();
        Session session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(session.createTopic("t")).thenReturn(topic);

        assertSame(topic, factory.createTopic(session, "t"));
    }

    @Test
    public void defaultCreateQueueDelegatesToSession() throws Exception {
        MinimalFactory factory = new MinimalFactory();
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createQueue("q")).thenReturn(queue);

        assertSame(queue, factory.createQueue(session, "q"));
    }

    @Test
    public void defaultCloseDoesNothing() {
        // must not throw
        new MinimalFactory().close();
    }
}
