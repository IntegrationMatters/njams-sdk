package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertThrows;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.it.support.ToxiproxyControl;

public class JmsThroughProxySmokeIT {

    private ToxiproxyControl toxiproxy;
    private int proxyPort;

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        int controlPort = Integer.parseInt(props.getProperty("+toxiproxy.control"));
        proxyPort = Integer.parseInt(props.getProperty("+toxiproxy.jms"));

        toxiproxy = new ToxiproxyControl(controlPort);
        toxiproxy.createProxy("jms", "0.0.0.0:20000", "activemq:61616");
    }

    @After
    public void tearDown() throws Exception {
        toxiproxy.resetAll();
    }

    @Test
    public void connectsThroughTheProxyWhenHealthy() throws Exception {
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + proxyPort);
        try (Connection connection = factory.createConnection()) {
            connection.start();
        }
    }

    @Test
    public void downToxicBlocksTheConnection() throws Exception {
        toxiproxy.addToxic("jms", "jms-down", "timeout", Map.of("timeout", 1000));
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + proxyPort);
        factory.setConnectResponseTimeout(3000);
        assertThrows(JMSException.class, () -> {
            try (Connection connection = factory.createConnection()) {
                connection.start();
            }
        });
    }
}
