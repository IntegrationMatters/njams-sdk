package com.im.njams.sdk.jms.namespace;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import jakarta.jms.ConnectionFactory;

import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.jms.ActiveMqSslJmsFactory;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Carried in from njams-sdk's own {@code ActiveMqSslJmsFactoryTest}, where the same assertion is {@code @Ignore}d:
 * {@code ActiveMqSslJmsFactory} reflects on {@code org.apache.activemq.ActiveMQSslConnectionFactory} by name, so
 * the jakarta implementation can only cast the result where the ActiveMQ client on the classpath is the jakarta
 * one. This module is the only place that is true, so this is the only executing coverage of the jakarta
 * {@code createConnectionFactory()} path.
 * <p>
 * No broker is involved: an {@code ActiveMQSslConnectionFactory} is inert until {@code createConnection()}.
 */
public class JakartaActiveMqSslJmsFactoryTest {

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
