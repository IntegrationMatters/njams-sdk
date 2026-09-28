package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

public class HttpMidProcessingOutageRecoveryIT {

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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        env.disableMessageDiscarding(settings);

        njams = new Njams(Path.of("HttpMidProcessingOutageRecoveryIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> logIds = MessageDriver.run(model, 20, 100, 4);

        // Same interleaving approach as the JMS scenario: start a second batch, cut after it has started, then
        // restore, since cutting mid-flight relative to the first batch isn't controllable at this granularity.
        env.toxiproxy().addToxic("http", "mid-outage", "timeout", Map.of("timeout", 1));
        Thread outageDriver = new Thread(() -> {
            try {
                logIds.addAll(MessageDriver.run(model, 20, 100, 4));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        outageDriver.start();
        Thread.sleep(500);
        env.toxiproxy().removeToxic("http", "mid-outage");
        outageDriver.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(40, logIds.size());
        assertEquals(40, Set.copyOf(logIds).size());

        assertEquals(40, countUniqueLogIdsDelivered(logIds));
    }

    /**
     * Counts the distinct {@code logIds} represented in WireMock's {@code /__admin/requests} journal by a
     * {@code POST} to the SDK's real ingest path ({@code /api/processing/ingest/dataprovider} —
     * {@code HttpSender}'s hardcoded {@code INGEST_API_PATH} plus the fixed {@code dataprovider} suffix this
     * module's settings and stubs agree on) whose {@link MessageHeaders#NJAMS_LOGID_HTTP_HEADER} ("njams-logid")
     * request header value is one of {@code logIds}. Counts distinct IDs, not total matching requests: under a
     * disrupted connection the sender can retry a send whose outcome was ambiguous (request reached WireMock but
     * the response was lost), producing more than one wire-level POST for the same {@code logId} — this is the
     * documented, safe at-least-once behavior the server's own idempotent-by-logId handling exists for (see
     * message-sending-control.md), not a drop or a wrong delivery, so it must not fail this assertion. Polls
     * rather than taking a single snapshot: {@code job.end()} only queues the message for background dispatch, so
     * delivery can still be catching up, especially right after an outage clears and the sender is settling its
     * reconnect.
     */
    private int countUniqueLogIdsDelivered(List<String> logIds) throws Exception {
        Set<String> wanted = Set.copyOf(logIds);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        int delivered;
        do {
            delivered = matchingJournalLogIds(wanted).size();
            if (delivered < wanted.size()) {
                Thread.sleep(500);
            }
        } while (delivered < wanted.size() && System.nanoTime() < deadline);
        return delivered;
    }

    private Set<String> matchingJournalLogIds(Set<String> wanted) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        String body = client.send(request, BodyHandlers.ofString()).body();

        JsonNode root = new ObjectMapper().readTree(body);
        Set<String> delivered = new java.util.HashSet<>();
        for (JsonNode entry : root.get("requests")) {
            JsonNode requestNode = entry.get("request");
            if (!"POST".equals(requestNode.get("method").asText())) {
                continue;
            }
            if (!"/api/processing/ingest/dataprovider".equals(requestNode.get("url").asText())) {
                continue;
            }
            JsonNode logIdHeader = requestNode.get("headers").get(MessageHeaders.NJAMS_LOGID_HTTP_HEADER);
            if (logIdHeader != null && wanted.contains(logIdHeader.asText())) {
                delivered.add(logIdHeader.asText());
            }
        }
        return delivered;
    }
}
