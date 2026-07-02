package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

public class HarnessSmokeIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    @Test
    public void rawJmsRoundTripThroughEmbeddedBroker() throws Exception {
        ConnectionFactorySupport cf = new ConnectionFactorySupport(broker.brokerUrl());
        Connection connection = cf.factory().createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("smoke.queue");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("hello"));
            MessageConsumer consumer = session.createConsumer(queue);
            TextMessage received = (TextMessage) consumer.receive(2000);
            assertNotNull("expected a message", received);
            assertEquals("hello", received.getText());
        } finally {
            connection.close();
        }
    }
}
