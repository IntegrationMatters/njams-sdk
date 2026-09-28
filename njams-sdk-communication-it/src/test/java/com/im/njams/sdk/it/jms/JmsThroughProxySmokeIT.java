package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertThrows;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.it.support.DockerEnvironment;

public class JmsThroughProxySmokeIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void connectsThroughTheProxyWhenHealthy() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlThroughProxy());
        try (Connection connection = factory.createConnection()) {
            connection.start();
        }
    }

    @Test
    public void downToxicBlocksTheConnection() throws Exception {
        env.toxiproxy().addToxic("jms", "jms-down", "timeout", Map.of("timeout", 1000));
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlThroughProxy());
        factory.setConnectResponseTimeout(3000);
        assertThrows(JMSException.class, () -> {
            try (Connection connection = factory.createConnection()) {
                connection.start();
            }
        });
    }
}
