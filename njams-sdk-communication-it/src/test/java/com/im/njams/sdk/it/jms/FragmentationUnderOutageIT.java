package com.im.njams.sdk.it.jms;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.DiscardObserver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

@RunWith(Parameterized.class)
public class FragmentationUnderOutageIT {


    @Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return Arrays.stream(DiscardMode.values()).map(m -> new Object[] { m }).collect(Collectors.toList());
    }

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Rule
    public DiscardObserver discards = new DiscardObserver();

    private final DiscardMode mode;
    private Njams njams;

    public FragmentationUnderOutageIT(DiscardMode mode) {
        this.mode = mode;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 60000)
    public void aFragmentedMessageIsNeverPartiallyDelivered() throws Exception {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);
        mode.apply(settings);
        // Force chunking well below the 200 KB payload driven below, so a single message is guaranteed to
        // fragment into multiple sends. Below SplitSupport.MIN_SIZE_LIMIT (10240), so the SDK clamps this up to
        // that minimum internally — still far smaller than the payload, so splitting still occurs.
        settings.put(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, "10000");

        njams = new Njams(Path.of("FragmentationUnderOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Armed before the driver starts, not after a fixed delay: a 200 KB payload over localhost/Docker can
        // fully send well inside any short window before a toxic lands, which would leave the outage never
        // actually intersecting the chunk sequence and the assertions below passing on the pure happy path
        // instead of on outage-interrupted delivery. Arming first guarantees every chunk attempt hits the outage
        // until it is removed, the same pattern MidProcessingOutageRecoveryIT uses for its own outage window.
        env.toxiproxy().addToxic("jms", "fragment-outage", "timeout", Map.of("timeout", 1));
        List<String> logIds = new ArrayList<>();
        Thread background = new Thread(() -> {
            try {
                // 200 KB payload against a ~10 KB chunk size guarantees multiple fragments per message.
                logIds.addAll(MessageDriver.run(model, 1, 200_000, 1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("jms", "fragment-outage");
        background.join(30000);

        assertEquals(1, logIds.size());
        assertFragmentsDeliveredPerMode(logIds.get(0));
    }

    /**
     * Confirms every chunk of the fragmented message with the given {@code logId} eventually arrived on
     * {@code njams.event}, tolerating duplicate chunk deliveries: under a disrupted connection the sender can
     * retry a chunk send whose outcome was ambiguous, the same documented at-least-once behavior asserted in
     * {@code MidProcessingOutageRecoveryIT} — a retried duplicate of a chunk already received must not fail this
     * check. What must never happen is a fragment gap: some chunk number between 1 and the declared total never
     * arriving at all, which would mean the server received a truncated/corrupt reassembly.
     */
    private void assertFragmentsDeliveredPerMode(String logId) throws Exception {
        String selector = MessageHeaders.NJAMS_LOGID_HEADER + " = '" + logId + "'";

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.event");
            MessageConsumer consumer = session.createConsumer(queue, selector);

            Set<Integer> chunkNumbersSeen = new HashSet<>();
            Integer declaredTotalChunks = null;
            Message message;
            while ((message = consumer.receive(mode.holdsMessages() ? 10_000 : 4_000)) != null) {
                int chunkNo = Integer.parseInt(message.getStringProperty(MessageHeaders.NJAMS_CHUNK_NO_HEADER));
                int totalChunks = Integer.parseInt(message.getStringProperty(MessageHeaders.NJAMS_CHUNKS_HEADER));
                declaredTotalChunks = totalChunks;
                chunkNumbersSeen.add(chunkNo);
            }

            if (mode.holdsMessages()) {
                assertEquals("Mode none must never discard", 0, discards.count());
            } else {
                // The outage covers the whole send, so the message must have been discarded as a whole.
                assertTrue("The fragmented message was driven into an outage and must be discarded",
                    discards.count() >= 1);
                if (chunkNumbersSeen.isEmpty()) {
                    return;
                }
            }
            assertTrue("Message never split into multiple chunks — the size settings failed to force fragmentation",
                declaredTotalChunks != null && declaredTotalChunks > 1);
            Set<Integer> expectedChunkNumbers = java.util.stream.IntStream.rangeClosed(1, declaredTotalChunks)
                .boxed().collect(Collectors.toSet());
            assertEquals("Fragment gap detected — not every chunk of the message arrived", expectedChunkNumbers,
                chunkNumbersSeen);
        }
    }
}
