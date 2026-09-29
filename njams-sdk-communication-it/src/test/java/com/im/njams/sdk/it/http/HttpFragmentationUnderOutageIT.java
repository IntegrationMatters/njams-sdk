package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

public class HttpFragmentationUnderOutageIT {

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
    public void aFragmentedMessageFullyResendsRatherThanPartiallyDelivering() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        env.disableMessageDiscarding(settings);
        // Force chunking well below the 200 KB payload driven below, so a single message is guaranteed to
        // fragment into multiple sends. Below SplitSupport.MIN_SIZE_LIMIT (10240), so the SDK clamps this up to
        // that minimum internally — still far smaller than the payload, so splitting still occurs.
        settings.put(NjamsSettings.PROPERTY_MAX_MESSAGE_SIZE, "10000");

        njams = new Njams(Path.of("HttpFragmentationUnderOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Armed before the driver starts, not after a fixed delay: a 200 KB payload over localhost/Docker can
        // fully send well inside any short window before a toxic lands, which would leave the outage never
        // actually intersecting the chunk sequence and the assertions below passing on the pure happy path
        // instead of on outage-interrupted delivery. Arming first guarantees every chunk attempt hits the outage
        // until it is removed, the same pattern MidProcessingOutageRecoveryIT uses for its own outage window.
        env.toxiproxy().addToxic("http", "fragment-outage", "timeout", Map.of("timeout", 1));
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
        Thread.sleep(500);
        env.toxiproxy().removeToxic("http", "fragment-outage");
        background.join(30000);

        assertEquals(1, logIds.size());
        assertFragmentsCompletelyDelivered(logIds.get(0));
    }

    /**
     * Confirms every chunk of the fragmented message with the given {@code logId} eventually reached WireMock's
     * request journal, tolerating duplicate chunk deliveries the same way {@code assertFragmentsCompletelyDelivered}
     * does in the JMS scenario — a retried duplicate of a chunk already received must not fail this check. What
     * must never happen is a fragment gap: some chunk number between 1 and the declared total never arriving at
     * all. Each chunk POST carries {@code application/json} content, matching {@code HttpSender.sendChunk}'s
     * request body ({@code HttpClientFactory.MEDIA_TYPE_JSON} — confirmed via source read; the SDK does not send
     * chunk bodies as {@code text/plain}), so this is not asserted separately.
     */
    private void assertFragmentsCompletelyDelivered(String logId) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        Set<Integer> chunkNumbersSeen = new HashSet<>();
        Integer declaredTotalChunks = null;
        while (System.nanoTime() < deadline) {
            declaredTotalChunks = collectChunks(logId, chunkNumbersSeen);
            if (declaredTotalChunks != null && chunkNumbersSeen.size() >= declaredTotalChunks) {
                break;
            }
            Thread.sleep(500);
        }

        assertTrue("Message never split into multiple chunks — the size settings failed to force fragmentation",
            declaredTotalChunks != null && declaredTotalChunks > 1);
        Set<Integer> expectedChunkNumbers = IntStream.rangeClosed(1, declaredTotalChunks)
            .boxed().collect(Collectors.toSet());
        assertEquals("Fragment gap detected — not every chunk of the message arrived", expectedChunkNumbers,
            chunkNumbersSeen);
    }

    private Integer collectChunks(String logId, Set<Integer> chunkNumbersSeen) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        String body = client.send(request, BodyHandlers.ofString()).body();

        JsonNode root = new ObjectMapper().readTree(body);
        Integer declaredTotalChunks = null;
        for (JsonNode entry : root.get("requests")) {
            JsonNode requestNode = entry.get("request");
            if (!"POST".equals(requestNode.get("method").asText())) {
                continue;
            }
            if (!"/api/processing/ingest/dataprovider".equals(requestNode.get("url").asText())) {
                continue;
            }
            JsonNode headers = requestNode.get("headers");
            JsonNode logIdHeader = headers.get(MessageHeaders.NJAMS_LOGID_HTTP_HEADER);
            if (logIdHeader == null || !logId.equals(logIdHeader.asText())) {
                continue;
            }
            JsonNode chunkNoHeader = headers.get(MessageHeaders.NJAMS_CHUNK_NO_HTTP_HEADER);
            JsonNode chunksHeader = headers.get(MessageHeaders.NJAMS_CHUNKS_HTTP_HEADER);
            if (chunkNoHeader == null || chunksHeader == null) {
                continue;
            }
            chunkNumbersSeen.add(Integer.parseInt(chunkNoHeader.asText()));
            declaredTotalChunks = Integer.parseInt(chunksHeader.asText());
        }
        return declaredTotalChunks;
    }
}
