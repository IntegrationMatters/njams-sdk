package com.im.njams.sdk.it.http;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.SdkThreads;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Startup with {@code startup.failbehavior=fail} and the target unusable (unreachable, or {@code HEAD} answers
 * {@code 404}): the SDK must shut down fully. This is independent of the discard policy, so it is not repeated per
 * mode. The reconnect counterpart is {@link HttpStartupReconnectIT}.
 */
public class HttpStartupOutageIT {

    /** Longer than the sender's reconnect interval, so a reconnect that wrongly kept running would show up. */
    private static final long OBSERVATION_MS = 4_000;

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
    public void startFailsOnTransportLevelOutage() throws Exception {
        env.toxiproxy().addToxic("http", "startup-down", "timeout", Map.of("timeout", 1));
        njams = startWithFailBehavior();

        assertStartFailedAndSdkShutDown(() -> env.toxiproxy().removeToxic("http", "startup-down"));
    }

    @Test(timeout = 60000)
    public void startFailsWhenHeadReturns404() throws Exception {
        String notFoundMapping = loadOnDemandMapping("head-not-found.json");
        njams = startWithFailBehavior();

        assertStartFailedAndSdkShutDown(() -> deleteMapping(notFoundMapping));
    }

    private interface Recovery {
        void run() throws Exception;
    }

    /**
     * {@code start()} reports failure, the instance is inactive, none of the SDK's threads survive, and once the
     * target is usable again nothing reaches it any more — the journal must not grow by a single request.
     */
    private void assertStartFailedAndSdkShutDown(Recovery recovery) throws Exception {
        boolean started = njams.start();

        assertFalse("start() must report failure when the target is unusable at startup", started);
        assertFalse("A failed start must leave the instance inactive", njams.isStarted());
        assertNoSdkThreadsAlive("after the failed start");

        long requestsAfterFailedStart = settledJournalSize();
        recovery.run();
        Thread.sleep(OBSERVATION_MS);

        assertEquals("A failed start must not reconnect or send anything once the target is usable again",
            requestsAfterFailedStart, WireMockJournal.countAll(env));
        assertNoSdkThreadsAlive("after the target became usable again");
    }

    private static void assertNoSdkThreadsAlive(String when) throws InterruptedException {
        Set<String> survivors = SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.SENDER_STARTUP,
            SdkThreads.SENDER_RECONNECTOR, SdkThreads.RECEIVER);
        assertTrue("SDK threads survived the failed start " + when + ": " + survivors, survivors.isEmpty());
    }

    /** Waits until the journal has not grown for two seconds (the failed startup's own requests are still landing). */
    private long settledJournalSize() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        long last = WireMockJournal.countAll(env);
        long stableSince = System.nanoTime();
        while (System.nanoTime() < deadline) {
            Thread.sleep(500);
            long current = WireMockJournal.countAll(env);
            if (current != last) {
                last = current;
                stableSince = System.nanoTime();
            } else if (System.nanoTime() - stableSince >= Duration.ofSeconds(2).toNanos()) {
                return current;
            }
        }
        return last;
    }

    private Njams startWithFailBehavior() {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");
        return new Njams(Path.of("HttpStartupOutageIT"), "1.0.0", "CommunicationIT", settings);
    }

    /** Loads an on-demand stub and returns its id, so it can be removed again by exactly that id. */
    private String loadOnDemandMapping(String classpathResource) throws IOException, InterruptedException {
        String body;
        try (var in = getClass().getClassLoader().getResourceAsStream("wiremock/on-demand/" + classpathResource)) {
            body = new String(in.readAllBytes());
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        String response = client.send(request, BodyHandlers.ofString()).body();
        return new ObjectMapper().readTree(response).path("id").asText();
    }

    private void deleteMapping(String id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings/" + id))
            .DELETE()
            .build();
        HttpClient.newHttpClient().send(request, BodyHandlers.discarding());
    }
}
