package com.im.njams.sdk.it.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.communication.MessageHeaders;

/**
 * Polls WireMock's {@code /__admin/requests} journal so a scenario can assert on what was actually received,
 * instead of on timing around the SDK's own (always-async) send call. Delivery via the SDK's background sender
 * threads is never synchronous with {@code MessageDriver.run(...)} returning, so every assertion here polls up to
 * a deadline rather than taking a single snapshot.
 */
public final class WireMockJournal {

    /** The SDK's real ingest path — {@code HttpSender}'s hardcoded {@code INGEST_API_PATH} plus the fixed
     * {@code dataprovider} suffix this module's settings and stubs agree on. */
    public static final String INGEST_PATH = "/api/processing/ingest/dataprovider";

    private WireMockJournal() {
    }

    /**
     * Counts journal entries matching the given method, URL path, and (if non-null) {@code njams-logid} header.
     */
    public static long countMatching(DockerEnvironment env, String method, String urlPath, String logId)
        throws IOException, InterruptedException {
        long count = 0;
        for (JsonNode entry : requests(env)) {
            JsonNode request = entry.get("request");
            if (!method.equals(request.get("method").asText()) || !urlPath.equals(request.get("url").asText())) {
                continue;
            }
            if (logId != null) {
                JsonNode logIdHeader = request.get("headers").get(MessageHeaders.NJAMS_LOGID_HTTP_HEADER);
                if (logIdHeader == null || !logId.equals(logIdHeader.asText())) {
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    /**
     * Polls {@link #countMatching} until {@code satisfied} accepts the count or {@code timeout} elapses, returning
     * whatever the last observed count was either way — the caller asserts on that value, so a still-unsatisfied
     * result fails with the actual count in the assertion message rather than an opaque timeout exception.
     */
    public static long awaitCount(DockerEnvironment env, String method, String urlPath, String logId,
        LongPredicate satisfied, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        long count;
        do {
            count = countMatching(env, method, urlPath, logId);
            if (satisfied.test(count)) {
                return count;
            }
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        return count;
    }

    /**
     * Waits until the startup project message (sent once during {@code Njams.start()}/process-model registration)
     * has been recorded in the journal. A scenario that loads an on-demand fault mapping right after starting
     * {@code Njams} must wait for this first — otherwise the project message, not the job message the scenario
     * actually targets, is the one that races into the fault window and absorbs its retries/reconnect, while the
     * job message itself slips through afterward once the fault has already been reset.
     */
    public static void awaitProjectMessageSent(DockerEnvironment env, Duration timeout)
        throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            for (JsonNode entry : requests(env)) {
                JsonNode request = entry.get("request");
                if (!"POST".equals(request.get("method").asText()) || !INGEST_PATH.equals(request.get("url").asText())) {
                    continue;
                }
                JsonNode messageType = request.get("headers").get(MessageHeaders.NJAMS_MESSAGETYPE_HTTP_HEADER);
                if (messageType != null && MessageHeaders.MESSAGETYPE_PROJECT.equals(messageType.asText())) {
                    return;
                }
            }
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Timed out waiting for the startup project message to be recorded");
    }

    private static List<JsonNode> requests(DockerEnvironment env) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        String body = client.send(request, BodyHandlers.ofString()).body();
        JsonNode root = new ObjectMapper().readTree(body);
        List<JsonNode> result = new ArrayList<>();
        root.get("requests").forEach(result::add);
        return result;
    }
}
