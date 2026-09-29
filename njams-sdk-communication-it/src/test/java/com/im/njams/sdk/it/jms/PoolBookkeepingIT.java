package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * Regression guard for {@code SenderPool}'s bookkeeping collections: they must stay bounded to
 * {@code maxSenderThreads} under concurrent load rather than leaking entries, and the pool's own idea of how many
 * senders it holds must agree with the broker's independently-observed reality. The second half is the point of
 * this test — {@code pooledSenderCount()} alone could stay "bounded" while silently disagreeing with the real
 * connection count (e.g. a sender counted as pooled but never actually closed, or vice versa); cross-checking
 * against ActiveMQ's own live JMX state (via the Jolokia REST endpoint {@link DockerEnvironment#jolokiaUrl()}
 * already provisions but no other scenario in this module consumes) catches that class of drift that an
 * in-process-only assertion cannot.
 */
public class PoolBookkeepingIT {

    private static final int MAX_SENDER_THREADS = 4;

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
    @SuppressWarnings("deprecation") // Njams.getSender() is deprecated for removal, but is the only way to reach
                                      // NjamsSender's test-support pooledSenderCount() accessor from outside the SDK.
    public void pooledSenderCountStaysBoundedAndMatchesTheBrokersOwnConnectionCount() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, String.valueOf(MAX_SENDER_THREADS));
        env.configureJms(settings);
        env.disableMessageDiscarding(settings);

        njams = new Njams(Path.of("PoolBookkeepingIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Warm the pool with a single healthy connection first, exactly as DegradedConnectIT does.
        MessageDriver.run(model, 5, 100, 1);

        // Same technique DegradedConnectIT uses to force the pool to grow beyond its one warm sender: a uniform
        // latency toxic makes each send slow enough that concurrent submissions outrun the executor's core thread,
        // filling its bounded dispatch queue (capacity 8, NjamsSender's PROPERTY_MAX_QUEUE_LENGTH default) and
        // forcing growth up to maxSenderThreads. 2000ms latency and this count/concurrency are the same values
        // DegradedConnectIT already confirmed (via Docker run) reliably force that growth without tripping
        // ActiveMQ's wire-format-negotiation timeout.
        env.toxiproxy().addToxic("jms", "pool-growth-load", "latency", Map.of("latency", 2000, "jitter", 0));
        List<String> logIds = MessageDriver.run(model, 8, 100, 8);
        awaitAllDelivered(logIds);

        int pooledSenderCount = njams.getSender().pooledSenderCount();
        assertTrue("Pool should have grown under concurrent load but stayed bounded to maxSenderThreads="
            + MAX_SENDER_THREADS + ", was " + pooledSenderCount,
            pooledSenderCount > 1 && pooledSenderCount <= MAX_SENDER_THREADS);

        // The SDK's one JmsReceiver instance holds its own, separate JMS Connection (confirmed via JmsReceiver
        // source: a private Connection field distinct from any JmsSender's), so the broker's live connection count
        // is expected to be exactly the pool's sender count plus that one receiver connection.
        int brokerConnectionCount = currentBrokerConnectionCount();
        assertEquals("Broker's own live connection count (independently queried via Jolokia) must equal the pool's "
            + "bookkeeping count plus the SDK's one receiver connection", pooledSenderCount + 1,
            brokerConnectionCount);
    }

    /**
     * Waits until every message in {@code logIds} has actually been delivered to the {@code njams.event} queue, via
     * a JMS selector on {@link MessageHeaders#NJAMS_LOGID_HEADER} — same pattern as DegradedConnectIT, using a
     * direct (non-proxied, non-toxic-affected) connection so the wait itself isn't subject to the load toxic.
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
            assertTrue("All driven jobs must be delivered before checking pool/broker connection state; delivered "
                + delivered.size() + "/" + logIds.size(), delivered.size() == logIds.size());
        }
    }

    /**
     * Reads ActiveMQ's own {@code CurrentConnectionsCount} broker attribute via Jolokia — confirmed empirically
     * against the running container (not assumed from general ActiveMQ/Jolokia familiarity): the MBean is
     * {@code org.apache.activemq:type=Broker,brokerName=localhost} (the {@code apache/activemq-classic} image's
     * default broker name), and the attribute tracked 0 -> 2 across two independently-opened JMS connections in a
     * manual probe. Jolokia rejects requests whose {@code Origin} header is missing/{@code null} with a 403, so an
     * explicit same-origin value is set here.
     */
    private int currentBrokerConnectionCount() throws Exception {
        URI jolokiaUri = URI.create(env.jolokiaUrl());
        String credentials = Base64.getEncoder().encodeToString(jolokiaUri.getUserInfo().getBytes());
        URI requestUri = new URI(jolokiaUri.getScheme(), null, jolokiaUri.getHost(), jolokiaUri.getPort(),
            jolokiaUri.getPath(), null, null);
        String origin = jolokiaUri.getScheme() + "://" + jolokiaUri.getHost() + ":" + jolokiaUri.getPort();

        String requestBody = "{\"type\":\"read\",\"mbean\":\"org.apache.activemq:type=Broker,brokerName=localhost\","
            + "\"attribute\":\"CurrentConnectionsCount\"}";
        HttpRequest request = HttpRequest.newBuilder(requestUri)
            .header("Authorization", "Basic " + credentials)
            .header("Origin", origin)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        String body = HttpClient.newHttpClient().send(request, BodyHandlers.ofString()).body();
        JsonNode root = new ObjectMapper().readTree(body);
        return root.get("value").asInt();
    }
}
