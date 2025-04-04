package com.im.njams.sdk.communication.jms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.communication.jms.factory.JmsFactory;
import com.im.njams.sdk.communication.jms.factory.JndiJmsFactory;

public class EMSInitTest {

    private Properties props = null;

    @Before
    public void setup() {
        props = new Properties();
        props.put("njams.sdk.communication.jms.connectionFactory", "QueueConnectionFactory");
        props.put("njams.sdk.communication.jms.username", "njams");
        props.put("njams.sdk.communication.jms.password", "njams");
        props.put("njams.sdk.communication.jms.destination", "njams");
        props.put("njams.sdk.communication.jms.java.naming.factory.initial",
            "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
        props.put("njams.sdk.communication.jms.java.naming.provider.url", "tcp://vslamq01:61616");
        props.put("njams.sdk.communication.jms.destination", "dynamicQueues/njams.sophie");
        props.put("njams.sdk.communication.jms.connectionFactory", "ConnectionFactory");
        props.put("njams.sdk.communication.jms.destination.commands", "dynamicTopics/njams.sophie.commands");

    }

    @Test
    public void testConnectToActiveMQ() throws JMSException, NamingException {
        try (JmsFactory factory = JmsFactory.find(props)) {
            assertTrue(factory instanceof JndiJmsFactory);
            ConnectionFactory conFactory = factory.createConnectionFactory();
            assertTrue(conFactory instanceof ActiveMQConnectionFactory);
            Connection con = conFactory.createConnection();
            assertNotNull(con);
            Session session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = factory.createQueue(session, "njams.event");
            assertNotNull(queue);
        }
    }

}
