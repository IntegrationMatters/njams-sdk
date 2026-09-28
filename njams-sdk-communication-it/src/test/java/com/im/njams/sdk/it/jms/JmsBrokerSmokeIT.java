package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.it.support.DockerEnvironment;

public class JmsBrokerSmokeIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void sendsAndReceivesOneMessage() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.test.smoke");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            TextMessage sent = session.createTextMessage("smoke-test");
            producer.send(sent);

            Message received = consumer.receive(5000);
            assertEquals("smoke-test", ((TextMessage) received).getText());
        }
    }
}
