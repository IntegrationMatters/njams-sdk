package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Resolves the SDK's JMS factory SPI against the real broker: the JNDI lookup itself, the connection factory
 * it yields, and a destination created through it. The other JMS scenarios drive a whole {@code Njams} and
 * therefore exercise this path only implicitly.
 */
public class JndiJmsFactorySmokeIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void resolvesTheJndiFactoryAndCreatesADestination() throws Exception {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlDirect());
        env.configureJms(settings);

        try (JmsFactory factory = JmsFactory.find(settings)) {
            assertTrue(factory instanceof JndiJmsFactory);
            ConnectionFactory connectionFactory = factory.createConnectionFactory();
            assertTrue(connectionFactory instanceof ActiveMQConnectionFactory);
            try (Connection connection = connectionFactory.createConnection()) {
                Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                Queue queue = factory.createQueue(session, "njams.event");
                assertNotNull(queue);
            }
        }
    }
}
