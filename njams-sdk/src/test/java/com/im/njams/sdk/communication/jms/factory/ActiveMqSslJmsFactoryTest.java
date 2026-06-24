package com.im.njams.sdk.communication.jms.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Unit tests for {@link ActiveMqSslJmsFactory}: identity, JNDI inheritance and the reflective
 * creation of an {@code ActiveMQSslConnectionFactory}.
 */
public class ActiveMqSslJmsFactoryTest {

    @Test
    public void nameAndToString() {
        ActiveMqSslJmsFactory factory = new ActiveMqSslJmsFactory();
        assertEquals(ActiveMqSslJmsFactory.NAME, factory.getName());
        assertTrue(factory.toString().contains(ActiveMqSslJmsFactory.NAME));
    }

    @Test
    public void inheritsFromJndiJmsFactory() {
        // it reuses JNDI for queue/topic lookup, only the connection factory differs
        assertTrue(new ActiveMqSslJmsFactory() instanceof JndiJmsFactory);
    }

    @Test
    public void initCreatesActiveMqSslConnectionFactory() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, "ssl://localhost:61617");

        ActiveMqSslJmsFactory factory = new ActiveMqSslJmsFactory();
        factory.init(ClientSettings.from(props));

        ConnectionFactory connectionFactory = factory.createConnectionFactory();
        assertNotNull(connectionFactory);
        assertTrue("expected an ActiveMQ SSL connection factory, but was: " + connectionFactory.getClass(),
                connectionFactory.getClass().getName().contains("ActiveMQSslConnectionFactory"));
    }
}
