package com.im.njams.sdk.it.support;

import static com.im.njams.sdk.it.support.WireMockJournal.INGEST_PATH;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.communication.MessageHeaders;

/**
 * Counts how often each driven {@code logId} actually reached the broker / WireMock. Delivery is always
 * asynchronous, so both variants wait until the outcome is settled: under {@code none} every job has arrived,
 * under the other modes every job has either arrived or is accounted for by a discard. A short tail window then
 * catches a duplicate that lands right after the last first delivery.
 */
public final class Deliveries {

    private static final long TAIL_MS = 2_000;

    private Deliveries() {
    }

    public static Map<String, Integer> viaJms(DockerEnvironment env, Collection<String> logIds, DiscardMode mode,
        IntSupplier discards) throws Exception {
        String inClause = logIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        String selector = MessageHeaders.NJAMS_LOGID_HEADER + " IN (" + inClause + ")";
        long perMessageTimeoutMs = mode.holdsMessages() ? 10_000 : 4_000;

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlDirect());
        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("njams.event");
            MessageConsumer consumer = session.createConsumer(queue, selector);
            Map<String, Integer> deliveries = new HashMap<>();
            Message message;
            while (!settled(mode, logIds.size(), deliveries.size(), discards)
                && (message = consumer.receive(perMessageTimeoutMs)) != null) {
                deliveries.merge(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER), 1, Integer::sum);
            }
            while ((message = consumer.receive(TAIL_MS)) != null) {
                deliveries.merge(message.getStringProperty(MessageHeaders.NJAMS_LOGID_HEADER), 1, Integer::sum);
            }
            return deliveries;
        }
    }

    public static Map<String, Integer> viaHttp(DockerEnvironment env, Collection<String> logIds, DiscardMode mode,
        IntSupplier discards) throws Exception {
        Set<String> wanted = new HashSet<>(logIds);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(mode.holdsMessages() ? 30 : 10);
        Map<String, Integer> deliveries = httpSnapshot(env, wanted);
        while (!settled(mode, wanted.size(), deliveries.size(), discards) && System.nanoTime() < deadline) {
            Thread.sleep(300);
            deliveries = httpSnapshot(env, wanted);
        }
        Thread.sleep(TAIL_MS);
        return httpSnapshot(env, wanted);
    }

    private static boolean settled(DiscardMode mode, int driven, int delivered, IntSupplier discards) {
        return mode.holdsMessages() ? delivered >= driven : delivered + discards.getAsInt() >= driven;
    }

    private static Map<String, Integer> httpSnapshot(DockerEnvironment env, Set<String> wanted) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        JsonNode root = new ObjectMapper().readTree(client.send(request, BodyHandlers.ofString()).body());
        Map<String, Integer> deliveries = new HashMap<>();
        for (JsonNode entry : root.get("requests")) {
            JsonNode requestNode = entry.get("request");
            if (!"POST".equals(requestNode.get("method").asText())
                || !INGEST_PATH.equals(requestNode.get("url").asText())) {
                continue;
            }
            JsonNode logIdHeader = requestNode.get("headers").get(MessageHeaders.NJAMS_LOGID_HTTP_HEADER);
            if (logIdHeader != null && wanted.contains(logIdHeader.asText())) {
                deliveries.merge(logIdHeader.asText(), 1, Integer::sum);
            }
        }
        return deliveries;
    }
}
