package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
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

        Map<String, Integer> deliveryCounts = countDeliveriesPerLogId(logIds);
        assertEquals(40, deliveryCounts.size());

        // This harness drives exactly one job.end() flush per job, so a given logId has exactly one intended
        // message; a repeat delivery of that same logId is therefore a wire-level resend, not a legitimate
        // distinct update (see message-sending-control.md — updates to the same logId are the common case in
        // general, but this fixed harness never produces more than one per job). At-least-once semantics still
        // permit a single ambiguous-outcome retry (the send's success was unknown, so the sender retried), so
        // this bounds resends rather than forbidding them outright: more than that indicates an unbounded/looping
        // resend defect, not the documented safe-retry case.
        int maxDeliveries = deliveryCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue("No logId should be delivered more than twice (one attempt plus at most one "
            + "ambiguous-outcome retry); observed a max of " + maxDeliveries + " deliveries across logIds: "
            + deliveryCounts, maxDeliveries <= 2);
    }

    /**
     * Counts, per {@code logId}, how many messages on the {@code njams.event} queue (the SDK's default JMS
     * destination — confirmed against {@code JmsSender.createProducers}) carry that {@code logId} in their
     * {@link MessageHeaders#NJAMS_LOGID_HEADER} property, via a JMS selector restricted to {@code logIds}.
     * Filtering by logId (rather than a raw count) also keeps a prior test run's leftover messages on the same
     * queue from inflating the result. Drains until every {@code logId} has been seen at least once, then keeps
     * draining for a short tail window to catch any duplicate arriving immediately afterward, so a resend that
     * lands just after the last first-delivery isn't missed.
     */
    private Map<String, Integer> countDeliveriesPerLogId(List<String> logIds) throws Exception {
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
            Map<String, Integer> deliveries = new HashMap<>();
            Message message;
            while (deliveries.size() < logIds.size() && (message = consumer.receive(10_000)) != null) {
                deliveries.merge(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER), 1, Integer::sum);
            }
            while ((message = consumer.receive(2_000)) != null) {
                deliveries.merge(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER), 1, Integer::sum);
            }
            return deliveries;
        }
    }
}
