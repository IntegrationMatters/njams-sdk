package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import jakarta.jms.ConnectionFactory;

import org.junit.Ignore;
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

    /**
     * Ignored, not removed: the only ActiveMQ client on this module's test classpath is the javax one, and
     * {@code ActiveMqSslJmsFactory} reflects on {@code org.apache.activemq.ActiveMQSslConnectionFactory} by
     * name, so here it always produces a {@code javax.jms} factory that this jakarta implementation cannot
     * cast. Making it run needs {@code activemq-client-jakarta}, whose packages collide with the javax
     * client's - that belongs in the jakarta namespace module, not here.
     */
    @Ignore("needs a jakarta ActiveMQ client, which cannot share this module's test classpath")
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
