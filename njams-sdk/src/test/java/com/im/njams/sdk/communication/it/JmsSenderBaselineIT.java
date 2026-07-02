package com.im.njams.sdk.communication.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.settings.ClientSettings;

public class JmsSenderBaselineIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    private static final String EVENT_QUEUE = "njams.event";

    static ClientSettings settings() {
        Properties p = new Properties();
        p.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        p.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, EmbeddedActiveMqJmsFactory.NAME);
        p.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        // NONE = never discard; block and retry until sent. This pins the guaranteed-delivery contract so the
        // reconnect test (Task 4) is deterministic. (The product DEFAULT is DISCARD, which drops messages while
        // disconnected — not the contract we baseline here.)
        p.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
        // keep a single sender thread for deterministic ordering in these baseline tests
        p.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        p.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        return ClientSettings.from(p);
    }

    private static LogMessage logMessage(String logId, String path) {
        LogMessage msg = new LogMessage();
        msg.setLogId(logId);
        msg.setPath(path);
        return msg;
    }

    /** Drains up to timeoutMs; returns the text bodies received from the event queue. */
    List<String> consumeEventQueue(int expectedAtLeast, long timeoutMs) throws Exception {
        List<String> bodies = new ArrayList<>();
        ConnectionFactorySupport cf = new ConnectionFactorySupport(broker.brokerUrl());
        // NOTE: javax.jms.Connection (JMS 1.1) is NOT AutoCloseable — use try/finally, not try-with-resources.
        Connection connection = cf.factory().createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(EVENT_QUEUE);
            MessageConsumer consumer = session.createConsumer(queue);
            final long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && bodies.size() < expectedAtLeast) {
                TextMessage m = (TextMessage) consumer.receive(100);
                if (m != null) {
                    bodies.add(m.getText());
                }
            }
        } finally {
            connection.close();
        }
        return bodies;
    }

    @Test
    public void sendsLogMessageThatArrivesOnTheEventQueue() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        try {
            sender.send(logMessage("log-1", ">a>b>"), "session-1");
            List<String> bodies = consumeEventQueue(1, 5000);
            assertEquals("exactly one message expected", 1, bodies.size());
            assertTrue("body should carry the logId", bodies.get(0).contains("log-1"));
        } finally {
            sender.close();
        }
    }

    @Test
    public void deliveryResumesAfterTransientBrokerOutage() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        try {
            // 1) prove connected: first message arrives
            sender.send(logMessage("before-outage", ">a>b>"), "session-1");
            assertEquals(1, consumeEventQueue(1, 5000).size());

            // 2) transient outage
            broker.stopBroker();

            // 3) enqueue a message during the outage; with DISCARD_POLICY=none the sender blocks and retries
            //    rather than dropping it
            sender.send(logMessage("during-outage", ">a>b>"), "session-1");

            // 4) restore the broker
            broker.startBroker();

            // 5) the buffered message is delivered once reconnected
            List<String> bodies = consumeEventQueue(1, 15000);
            assertEquals("message sent during the outage must be delivered after reconnect", 1, bodies.size());
            assertTrue(bodies.get(0).contains("during-outage"));

            // 6) prove the sender actually rebuilt its connection: stopBroker() severed the live JMS
            //    connection, so delivery above could only happen via a freshly (re)established connection.
            assertTrue("a client connection must be (re)established with the broker after recovery",
                Await.until(() -> broker.connectionCount() >= 1, 5000));
        } finally {
            sender.close();
        }
    }

    @Test
    public void closeReturnsPromptlyAndDeliversInFlightMessage() throws Exception {
        NjamsSender sender = new NjamsSender(settings());
        sender.send(logMessage("final", ">a>b>"), "session-1");

        long start = System.currentTimeMillis();
        sender.close();
        long elapsed = System.currentTimeMillis() - start;

        // close() drains with a 10s await; a healthy connection should terminate well within it
        assertTrue("close() should return promptly when connected, took " + elapsed + " ms", elapsed < 10_000);

        List<String> bodies = consumeEventQueue(1, 5000);
        assertEquals("the in-flight message should have been delivered before shutdown completed", 1, bodies.size());
        assertTrue(bodies.get(0).contains("final"));
    }
}
