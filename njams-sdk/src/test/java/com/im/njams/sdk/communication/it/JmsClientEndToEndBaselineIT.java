package com.im.njams.sdk.communication.it;

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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.jms.JmsSender;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class JmsClientEndToEndBaselineIT {

    @Rule
    public EmbeddedActiveMqBroker broker = new EmbeddedActiveMqBroker();

    private static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, JmsSender.COMMUNICATION_NAME);
        s.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, EmbeddedActiveMqJmsFactory.NAME);
        s.put(NjamsSettings.PROPERTY_JMS_DESTINATION, "njams");
        return s;
    }

    private List<String> drain(String queueName, int expectedAtLeast, long timeoutMs) throws Exception {
        List<String> bodies = new ArrayList<>();
        // NOTE: javax.jms.Connection (JMS 1.1) is NOT AutoCloseable — use try/finally, not try-with-resources.
        Connection connection = new ConnectionFactorySupport(broker.brokerUrl()).factory().createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            long deadline = System.currentTimeMillis() + timeoutMs;
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
    public void startRunJobStopDeliversMessages() throws Exception {
        Njams njams = new Njams(Path.of("SDK4", "IT"), "TEST", "SDK4", settings());
        ProcessModel process = njams.model().create("PROCESSES");
        ActivityModel start = process.createActivity("act", "Act", null);
        start.setStarter(true);

        assertTrue("Njams should start against the embedded broker", njams.start());

        Job job = process.createJob();
        job.start();
        job.createActivity(start).setStarter().build();
        job.end(); // flushes a LogMessage

        // project message (from start()) and log message (from job.end()) both land on njams.event by default
        List<String> bodies = drain("njams.event", 1, 10000);
        assertTrue("at least one message should be delivered end-to-end", bodies.size() >= 1);

        njams.stop();
    }
}
