package com.im.njams.sdk.it.http;

import static com.im.njams.sdk.it.support.WireMockJournal.INGEST_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class HttpRejectAndCongestionIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;
    private String currentOnDemandMappingId;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 30000)
    public void rejectedMessageIsDiscardedWithoutAffectingTheConnection() throws Exception {
        njams = startWithDiscardPolicy("NONE");
        // Baseline after startup's own connectivity check (always one HEAD), so later assertions can check for
        // growth caused by THIS test's own behavior, independent of how many other tests already ran in this JVM.
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        loadOnDemandMapping("post-413.json");

        List<String> firstJob = MessageDriver.run(model, 1, 100, 1);
        String rejectedLogId = firstJob.get(0);
        // 413 is message-rejected (HttpSender.isMessageRejected), which attemptWithSmoothing/sendWithRetry throw
        // on immediately regardless of discard policy — a single attempt, never retried. Settle briefly, then
        // confirm it never grew past that single attempt.
        Thread.sleep(1500);
        assertEquals("A permanently-rejected message must be attempted exactly once, never retried",
            1, WireMockJournal.countMatching(env, "POST", INGEST_PATH, rejectedLogId));

        // Send a second, unrelated job afterward on the same instance, once the target accepts again — if the
        // sender had been wrongly retired over the 413, this would need a reconnect (a HEAD in the journal)
        // instead of delivering immediately on the same connection.
        resetToOkMapping();
        List<String> secondJob = MessageDriver.run(model, 1, 100, 1);
        String deliveredLogId = secondJob.get(0);
        long delivered = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, deliveredLogId, c -> c >= 1,
            Duration.ofSeconds(10));
        assertTrue("Second job must be delivered on the same, still-healthy connection", delivered >= 1);
        assertEquals("A message-rejected (413) failure must never trigger a reconnect", headBaseline,
            WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null));
    }

    @Test(timeout = 30000)
    public void congestionRetriesLocallyUnderNonDiscardPolicies() throws Exception {
        njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        loadOnDemandMapping("post-429.json");

        // Under a non-DISCARD policy this call is expected to block, retrying locally, until the mapping is
        // reset back to 200 from a concurrently-scheduled reset — proving it never gives up, retires the
        // sender, or reconnects on 429.
        Thread resetter = new Thread(() -> {
            try {
                Thread.sleep(2000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        resetter.start();
        List<String> jobs = MessageDriver.run(model, 1, 100, 1);
        String logId = jobs.get(0);
        resetter.join();

        long delivered = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, logId, c -> c >= 1,
            Duration.ofSeconds(15));
        assertTrue("Message must eventually be delivered once congestion clears", delivered >= 1);
        long attempts = WireMockJournal.countMatching(env, "POST", INGEST_PATH, logId);
        assertTrue("Congestion under a non-DISCARD policy must be retried locally (multiple attempts observed), "
            + "not given up on after one try; observed " + attempts, attempts >= 2);
        assertEquals("Congestion (429) must never trigger a reconnect — it is retried on the same connection",
            headBaseline, WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null));
    }

    @Test(timeout = 15000)
    public void congestionDiscardsImmediatelyWithoutDelayUnderDiscardPolicy() throws Exception {
        njams = startWithDiscardPolicy("DISCARD");
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        loadOnDemandMapping("post-429.json");

        long start = System.currentTimeMillis();
        List<String> jobs = MessageDriver.run(model, 1, 100, 1);
        long elapsedMs = System.currentTimeMillis() - start;
        String logId = jobs.get(0);

        Thread.sleep(1500);
        long attempts = WireMockJournal.countMatching(env, "POST", INGEST_PATH, logId);
        long headCount = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);

        assertTrue("DISCARD must give up immediately, took " + elapsedMs + "ms", elapsedMs < 3000);
        assertEquals("DISCARD must give up after exactly one attempt, never retry", 1, attempts);
        assertEquals("Discarding under congestion must never trigger a reconnect", headBaseline, headCount);
    }

    @Test(timeout = 30000)
    public void applicationLevel503IsTreatedAsConnectionProblemNotCongestion() throws Exception {
        njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        loadOnDemandMapping("post-503.json");

        Thread recovery = new Thread(() -> {
            try {
                // AbstractSender.SMOOTHING_DELAYS_MS (50+200+750ms) retries any non-rejected failure locally
                // before escalating, so a reset at or before that ~1s budget can let the message succeed via
                // local smoothing alone, without ever reaching the reconnect path this test targets. Must
                // outlast that budget so the escalation this test asserts on actually happens first.
                Thread.sleep(2000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        recovery.start();
        // 503 is classified as a connection problem, not congestion (HttpSender.isCongestion() only matches a
        // repeated 429 — confirmed via source read), so this exercises the reconnect path rather than local
        // congestion-retry; both would eventually succeed, but the classification itself is what SDK-476 fixed.
        List<String> jobs = MessageDriver.run(model, 1, 100, 1);
        String logId = jobs.get(0);
        recovery.join();

        long headAttempts = WireMockJournal.awaitCount(env, "HEAD", INGEST_PATH, null, c -> c > headBaseline,
            Duration.ofSeconds(15));
        assertTrue("A 503 must be classified as a connection problem, triggering a reconnect (HEAD) — this is "
            + "the exact regression SDK-476 fixed", headAttempts > headBaseline);
        long delivered = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, logId, c -> c >= 1,
            Duration.ofSeconds(15));
        assertTrue("Message must eventually be delivered once the connection recovers", delivered >= 1);
    }

    private Njams startWithDiscardPolicy(String policy) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
        Njams njams = new Njams(Path.of("HttpRejectAndCongestionIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        return njams;
    }

    /**
     * Loads an on-demand stub and tracks its id, so a later {@link #resetToOkMapping()} can remove exactly this
     * one mapping instead of resetting the whole store. Reset-all endpoints ({@code POST /mappings/reset},
     * {@code DELETE /mappings}) are deliberately never used here: confirmed via WireMock's own source
     * ({@code StoreBackedStubMappings.resetMappings()} -> {@code removeAllPersisted()} ->
     * {@code mappingsSaver.removeAll()}) that a store-wide reset deletes the backing JSON files of every
     * *persisted* (file-loaded) stub mapping from disk -- and since this module's WireMock container mounts
     * {@code wiremock/mappings/} read-write (`pom.xml`), that reaches straight through to the real source files
     * (`head-available.json`/`post-ok.json`), permanently destroying them for the rest of the Docker session.
     * Removing only the specific on-demand mapping this test itself added, by id, never touches those files or
     * the request journal.
     */
    private String loadOnDemandMapping(String classpathResource) throws Exception {
        String body = new String(getClass().getClassLoader()
            .getResourceAsStream("wiremock/on-demand/" + classpathResource).readAllBytes());
        HttpResponse<String> response = post(env.wireMockAdminUrl() + "/mappings", body);
        return new ObjectMapper().readTree(response.body()).path("id").asText();
    }

    private void resetToOkMapping() throws Exception {
        if (currentOnDemandMappingId != null) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest
                .newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings/" + currentOnDemandMappingId))
                .DELETE()
                .build();
            client.send(request, BodyHandlers.discarding());
        }
        currentOnDemandMappingId = loadOnDemandMapping("../mappings/post-ok.json");
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        return client.send(request, BodyHandlers.ofString());
    }
}
