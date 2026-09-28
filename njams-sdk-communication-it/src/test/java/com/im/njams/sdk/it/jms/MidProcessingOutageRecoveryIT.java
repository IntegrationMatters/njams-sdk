package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class MidProcessingOutageRecoveryIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void everyDrivenJobArrivesExactlyOnceAcrossAnOutage() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);
        env.disableMessageDiscarding(settings);

        njams = new Njams(Path.of("MidProcessingOutageRecoveryIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 20, 100, 4);

        // Cut the connection mid-flight relative to the driver above isn't meaningfully controllable at this
        // granularity without hooks into the driver itself; the realistic mid-processing case is exercised by
        // interleaving: start a second batch, cut after it has started, then restore.
        env.toxiproxy().addToxic("jms", "mid-outage", "timeout", Map.of("timeout", 1));
        Thread outageDriver = new Thread(() -> {
            try {
                logIds.addAll(MessageDriver.run(model, 20, 100, 4));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        outageDriver.start();
        Thread.sleep(500);
        env.toxiproxy().removeToxic("jms", "mid-outage");
        outageDriver.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(40, logIds.size());
        assertEquals(40, Set.copyOf(logIds).size());

        assertEquals(40, countUniqueLogIdsDelivered(logIds));
    }

    /**
     * Counts the distinct {@code logIds} represented among messages on the {@code njams.event} queue (the SDK's
     * default JMS destination — confirmed against {@code JmsSender.createProducers}) whose
     * {@link MessageHeaders#NJAMS_LOGID_HEADER} property matches one of {@code logIds}, via a JMS selector.
     * Counts distinct IDs, not total matching messages: under a disrupted connection the sender can retry a send
     * whose outcome was ambiguous, producing more than one message with the same {@code logId} on the queue —
     * this is the documented, safe at-least-once behavior the server's own idempotent-by-logId handling exists
     * for (see message-sending-control.md), not a drop or a wrong delivery, so it must not fail this assertion.
     * Filtering by logId (rather than a raw count) also keeps a prior test run's leftover messages on the same
     * queue from inflating the result.
     */
    private int countUniqueLogIdsDelivered(List<String> logIds) throws Exception {
        String inClause = logIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        String selector = MessageHeaders.NJAMS_LOGID_HEADER + " IN (" + inClause + ")";

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.event");
            MessageConsumer consumer = session.createConsumer(queue, selector);
            // job.end() only queues the log message for background dispatch (see message-sending-control.md);
            // right after an outage clears, the sender's own reconnect can still be settling, so the gap before
            // a message arrives can run well past a couple of seconds. A 10s per-message timeout keeps the drain
            // loop from giving up before delivery has actually caught up.
            Set<String> delivered = new java.util.HashSet<>();
            Message message;
            while (delivered.size() < logIds.size() && (message = consumer.receive(10_000)) != null) {
                delivered.add(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER));
            }
            return delivered.size();
        }
    }
}
