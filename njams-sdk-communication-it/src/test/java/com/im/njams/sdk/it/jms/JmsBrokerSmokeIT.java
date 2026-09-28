package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

public class JmsBrokerSmokeIT {

    @Test
    public void sendsAndReceivesOneMessage() throws Exception {
        int port = readActiveMqPort();
        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory("tcp://localhost:" + port);
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

    private static int readActiveMqPort() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        return Integer.parseInt(props.getProperty("+activemq.openwire"));
    }
}
