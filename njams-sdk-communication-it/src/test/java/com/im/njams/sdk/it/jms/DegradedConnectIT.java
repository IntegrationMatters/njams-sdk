package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
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

public class DegradedConnectIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 60000)
    public void concurrentPoolTrafficIsNotSerializedBehindOneSlowConnect() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");
        env.configureJms(settings);
        env.disableMessageDiscarding(settings);

        njams = new Njams(Path.of("DegradedConnectIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Warm the pool with a single healthy connection first.
        MessageDriver.run(model, 5, 100, 1);

        // A Toxiproxy toxic delays every byte on every link through the proxy uniformly (confirmed against
        // toxic_collection.go's chainAddToxic, which pushes a newly added toxic onto every currently open link,
        // not only ones opened afterward) -- it cannot create a "new connects are slow, everything else is fast"
        // split. What the B2 pool-lock fix actually buys is concurrency: before the fix, acquire()/release() held
        // one shared lock across the whole pool, so unrelated senders serialized behind whichever one happened to
        // be connecting/sending while blocked by the toxic. With the fix, all of them incur the same toxic delay
        // independently, in parallel, so wall-clock stays close to ONE toxic application instead of growing with
        // the number of concurrent operations.
        // Empirically confirmed (Docker run): a 15s latency here exceeds ActiveMQ's own wire-format-negotiation
        // timeout, so the connect doesn't just run slow -- it fails outright and falls into SenderConnector's
        // reconnect loop, which never catches up inside this test's timeout. Kept well under that ceiling instead.
        long toxicLatencyMs = 2000;
        env.toxiproxy().addToxic("jms", "slow-connect", "latency", Map.of("latency", toxicLatencyMs, "jitter", 0));

        long start = System.currentTimeMillis();
        // Concurrency forces the pool to grow from the 1 warm sender to maxSenderThreads=4, so 3 new connects
        // contend for the toxic (and for real JMS wire-negotiation overhead) while the toxic is active.
        List<String> logIds = MessageDriver.run(model, 8, 100, 8);
        awaitAllDelivered(logIds);
        long elapsedMs = System.currentTimeMillis() - start;

        // Empirically confirmed (Docker run, timestamped SenderPool logs): all 3 growth connects start within
        // ~15ms of each other and all 3 complete within ~12ms of each other too, each taking ~8s total -- 2s
        // toxic latency plus ~6s of real JMS wire-negotiation/JNDI overhead in this Docker/Toxiproxy setup, which
        // toxicLatencyMs alone does not capture. Parallel growth therefore takes roughly ONE connect's real
        // duration (observed consistently ~12-13s end to end across runs); serialized growth would take roughly
        // 3x that (~24s+, one connect after another). The bound below sits between the two, with margin.
        assertTrue("Pool traffic was serialized behind the shared toxic delay (took " + elapsedMs + "ms)",
            elapsedMs < 20000);
    }

    /**
     * Waits until every message in {@code logIds} has actually been delivered to the {@code njams.event} queue
     * (not merely enqueued by {@code job.end()} for background dispatch), via a JMS selector on
     * {@link MessageHeaders#NJAMS_LOGID_HEADER}.
     */
    private void awaitAllDelivered(List<String> logIds) throws Exception {
        String inClause = logIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        String selector = MessageHeaders.NJAMS_LOGID_HEADER + " IN (" + inClause + ")";

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.event");
            MessageConsumer consumer = session.createConsumer(queue, selector);
            Set<String> delivered = new HashSet<>();
            Message message;
            while (delivered.size() < logIds.size()
                && (message = consumer.receive(TimeUnit.SECONDS.toMillis(30))) != null) {
                delivered.add(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER));
            }
            assertTrue("All driven jobs must be delivered before measuring elapsed time; delivered "
                + delivered.size() + "/" + logIds.size(), delivered.size() == logIds.size());
        }
    }
}
