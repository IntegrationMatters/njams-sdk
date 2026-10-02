package com.im.njams.sdk.it.http;

import java.util.Properties;
import static com.im.njams.sdk.it.support.WireMockJournal.INGEST_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.DiscardObserver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Scenarios 7, 8 and 9a, once per discard mode. Only the discard-mode-dependent parts differ per mode: a rejected
 * message ({@code 413}) is dropped once in every mode; congestion ({@code 429}) is retried locally under
 * {@code none}/{@code onconnectionloss} and dropped after one attempt under {@code discard}; a {@code 503} is a
 * connection problem in every mode, i.e. held under {@code none} and held-or-discarded under the others.
 */
@RunWith(Parameterized.class)
public class HttpRejectAndCongestionIT {

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
    private String currentOnDemandMappingId;

    public HttpRejectAndCongestionIT(DiscardMode mode) {
        this.mode = mode;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 30000)
    public void rejectedMessageIsDiscardedWithoutAffectingTheConnection() throws Exception {
        njams = start();
        // Baseline after startup's own connectivity check (always one HEAD), so later assertions can check for
        // growth caused by THIS test's own behavior, independent of how many other tests already ran in this JVM.
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        currentOnDemandMappingId = loadOnDemandMapping("post-413.json");

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
        assertEquals("Exactly the rejected message must be counted as discarded, in every mode", 1,
            discards.count());
    }

    @Test(timeout = 30000)
    public void congestionIsRetriedOrDiscardedAccordingToTheDiscardMode() throws Exception {
        if (mode == DiscardMode.DISCARD) {
            congestionDiscardsImmediatelyWithoutDelay();
        } else {
            congestionRetriesLocally();
        }
    }

    private void congestionRetriesLocally() throws Exception {
        njams = start();
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        currentOnDemandMappingId = loadOnDemandMapping("post-429.json");

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
        assertEquals("Nothing may be discarded while congestion is waited out", 0, discards.count());
    }

    private void congestionDiscardsImmediatelyWithoutDelay() throws Exception {
        njams = start();
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        currentOnDemandMappingId = loadOnDemandMapping("post-429.json");

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
        assertEquals("The congested message must be counted as discarded exactly once", 1, discards.count());
    }

    @Test(timeout = 30000)
    public void applicationLevel503IsTreatedAsConnectionProblemNotCongestion() throws Exception {
        njams = start();
        long headBaseline = WireMockJournal.countMatching(env, "HEAD", INGEST_PATH, null);
        ProcessModel model = FixedProcessModel.build(njams);
        // Let the startup project message land on the still-healthy (default OK) mapping before introducing the
        // fault, so the fault applies only to the job message under test — not to this unrelated concurrent send.
        WireMockJournal.awaitProjectMessageSent(env, Duration.ofSeconds(10));
        currentOnDemandMappingId = loadOnDemandMapping("post-503.json");

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
            Duration.ofSeconds(mode.holdsMessages() ? 15 : 5));
        if (mode.holdsMessages()) {
            assertTrue("Message must eventually be delivered once the connection recovers", delivered >= 1);
            assertEquals("Mode none must never discard", 0, discards.count());
        } else {
            // The 503 leaves HEAD healthy, so the reconnect completes almost at once: the message is either
            // dropped while the group is still reconnecting, or re-sent on the fresh sender until the stub
            // recovers. Which one wins is a race inside the SDK, so both are valid -- but it must be one of them.
            assertTrue("Under " + mode + " the message must be delivered or counted as discarded; delivered="
                + delivered + ", discarded=" + discards.count(), delivered >= 1 || discards.count() >= 1);
        }
    }

    private Njams start() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        mode.apply(settings);
        Njams njams = new Njams(Path.of("HttpRejectAndCongestionIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        return njams;
    }

    /**
     * Loads an on-demand stub and tracks its id, so a later {@link #resetToOkMapping()} can remove exactly this
     * one mapping instead of clearing the whole store. {@code DELETE /mappings} is deliberately never used here:
     * confirmed against WireMock's source ({@code StoreBackedStubMappings.resetMappings()} ->
     * {@code removeAllPersisted()} -> {@code mappingsSaver.removeAll()}) and by running it against a copy of the
     * mappings folder, it deletes the backing JSON files of every *persisted* (file-loaded) stub mapping from disk
     * -- and since this module's WireMock container mounts {@code wiremock/mappings/} read-write (`pom.xml`), that
     * reaches straight through to the real source files (`head-available.json`/`post-ok.json`), permanently
     * destroying them for the rest of the Docker session. {@code POST /mappings/reset}, by contrast, keeps the
     * files and reloads them, which is why {@code DockerEnvironment} uses it between tests. Removing only the
     * specific on-demand mapping this test itself added, by id, never touches those files or the request journal.
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
