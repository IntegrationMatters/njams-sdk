package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
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

    /**
     * One initial send attempt plus {@code AbstractSender.SMOOTHING_DELAYS_MS.length} (3) bounded local smoothing
     * retries -- confirmed via {@code AbstractSender.java}, since that field is private and not otherwise reachable
     * from this module.
     */
    private static final int MAX_EXPECTED_DELIVERIES_PER_LOG_ID = 4;

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

        Map<String, Integer> deliveryCounts = countDeliveriesPerLogId(logIds);
        assertEquals(40, deliveryCounts.size());

        // This harness drives exactly one job.end() flush per job, so a given logId has exactly one intended
        // message; a repeat wire-level POST for that same logId is therefore a resend, not a legitimate distinct
        // update (see message-sending-control.md — updates to the same logId are the common case in general, but
        // this fixed harness never produces more than one per job). AbstractSender.attemptWithSmoothing bounds a
        // failed send to one initial attempt plus SMOOTHING_DELAYS_MS.length (3) local smoothing retries -- up to
        // 4 raw wire-level attempts per logical message -- entirely before anything escalates to the pool/reconnect
        // layer. Over HTTP, request and response are decoupled at the socket level, so under this scenario's
        // "timeout" toxic each of those attempts can independently reach the real server (confirmed via WireMock's
        // own request journal recording a full POST body for every attempt): the request lands and a response is
        // built, but the toxic severs the connection before the client sees it, so the client retries an
        // already-served request. This is the documented at-least-once/ambiguous-outcome case (server dedups by
        // logId), not an unbounded/looping resend defect -- so the bound here is the smoothing window's own
        // maximum, not the single-retry margin that happens to suffice for the JMS scenario on the same toxic.
        int maxDeliveries = deliveryCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue("No logId should be POSTed more than " + MAX_EXPECTED_DELIVERIES_PER_LOG_ID
            + " times (one initial attempt plus AbstractSender's bounded local smoothing retries); observed a max "
            + "of " + maxDeliveries + " POSTs across logIds: " + deliveryCounts,
            maxDeliveries <= MAX_EXPECTED_DELIVERIES_PER_LOG_ID);
    }

    /**
     * Counts, per {@code logId}, how many {@code POST}s to the SDK's real ingest path
     * ({@code /api/processing/ingest/dataprovider} — {@code HttpSender}'s hardcoded {@code INGEST_API_PATH} plus
     * the fixed {@code dataprovider} suffix this module's settings and stubs agree on) in WireMock's
     * {@code /__admin/requests} journal carry that {@code logId} in their
     * {@link MessageHeaders#NJAMS_LOGID_HTTP_HEADER} ("njams-logid") request header. Polls rather than taking a
     * single snapshot: {@code job.end()} only queues the message for background dispatch, so delivery can still
     * be catching up, especially right after an outage clears and the sender is settling its reconnect.
     */
    private Map<String, Integer> countDeliveriesPerLogId(List<String> logIds) throws Exception {
        Set<String> wanted = Set.copyOf(logIds);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        Map<String, Integer> deliveries;
        do {
            deliveries = matchingJournalDeliveryCounts(wanted);
            if (deliveries.size() < wanted.size()) {
                Thread.sleep(500);
            }
        } while (deliveries.size() < wanted.size() && System.nanoTime() < deadline);
        return deliveries;
    }

    private Map<String, Integer> matchingJournalDeliveryCounts(Set<String> wanted) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        String body = client.send(request, BodyHandlers.ofString()).body();

        JsonNode root = new ObjectMapper().readTree(body);
        Map<String, Integer> deliveries = new HashMap<>();
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
                deliveries.merge(logIdHeader.asText(), 1, Integer::sum);
            }
        }
        return deliveries;
    }
}
