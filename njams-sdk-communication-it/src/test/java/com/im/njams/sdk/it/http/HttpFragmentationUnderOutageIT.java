package com.im.njams.sdk.it.http;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import com.im.njams.sdk.communication.MessageHeaders;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.DiscardObserver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

@RunWith(Parameterized.class)
public class HttpFragmentationUnderOutageIT {


    @Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return Arrays.stream(DiscardMode.values()).map(m -> new Object[] { m }).collect(Collectors.toList());
    }

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Rule
    public DiscardObserver discards = new DiscardObserver();

    private static final String NL = System.lineSeparator();

    private final DiscardMode mode;
    private Njams njams;

    public HttpFragmentationUnderOutageIT(DiscardMode mode) {
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
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        mode.apply(settings);
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
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("http", "fragment-outage");
        background.join(30000);

        assertEquals(1, logIds.size());
        assertFragmentsDeliveredPerMode(logIds.get(0));
    }

    /**
     * Confirms the fragmented message with the given {@code logId} never reached WireMock's request journal with
     * a fragment gap — some chunk number missing while a later one arrived. Duplicates are tolerated the same way
     * {@code assertFragmentsCompletelyDelivered} does in the JMS scenario: a retried duplicate of a chunk already
     * received must not fail this check.
     * <p>
     * What completeness is required depends on the discard mode:
     * <ul>
     * <li>{@code none} holds and retries until the outage clears, so every chunk from 1 to the declared total
     * must arrive.</li>
     * <li>A discarding mode drops the message where it stood, so the arriving set is a contiguous prefix 1..n,
     * possibly empty. It is not required to be complete: this module's README documents that the {@code
     * timeout} toxic used for the outage blocks only the response direction, so the chunk whose failure aborts
     * the sequence may already have reached WireMock. How long the prefix is depends on how far the sender got
     * before the outage stopped it, so its length is not asserted — only that it has no hole in it.</li>
     * </ul>
     * Each chunk POST carries {@code application/json} content, matching {@code HttpSender.sendChunk}'s request
     * body ({@code HttpClientFactory.MEDIA_TYPE_JSON} — confirmed via source read; the SDK does not send chunk
     * bodies as {@code text/plain}), so this is not asserted separately.
     */
    private void assertFragmentsDeliveredPerMode(String logId) throws Exception {
        long deadline = System.nanoTime()
            + java.util.concurrent.TimeUnit.SECONDS.toNanos(mode.holdsMessages() ? 30 : 8);
        Set<Integer> chunkNumbersSeen = new HashSet<>();
        Integer declaredTotalChunks = null;
        while (System.nanoTime() < deadline) {
            declaredTotalChunks = collectChunks(logId, chunkNumbersSeen);
            if (declaredTotalChunks != null && chunkNumbersSeen.size() >= declaredTotalChunks) {
                break;
            }
            Thread.sleep(500);
        }

        if (mode.holdsMessages()) {
            assertEquals("Mode none must never discard", 0, discards.count());
            assertFragmented(declaredTotalChunks);
            Set<Integer> expectedChunkNumbers = IntStream.rangeClosed(1, declaredTotalChunks)
                .boxed().collect(Collectors.toSet());
            assertEquals("Fragment gap detected — not every chunk of the message arrived."
                + describeIngestJournal(logId), expectedChunkNumbers, chunkNumbersSeen);
            return;
        }

        assertTrue("The fragmented message was driven into an outage and must be discarded",
            discards.count() >= 1);
        if (chunkNumbersSeen.isEmpty()) {
            return;
        }
        assertFragmented(declaredTotalChunks);
        assertTrue("More chunks arrived than the message declares: " + chunkNumbersSeen.size() + " of "
            + declaredTotalChunks + "." + describeIngestJournal(logId),
            chunkNumbersSeen.size() <= declaredTotalChunks);
        Set<Integer> expectedPrefix = IntStream.rangeClosed(1, chunkNumbersSeen.size())
            .boxed().collect(Collectors.toSet());
        assertEquals("Fragment gap detected — the chunks that arrived are not a contiguous prefix."
            + describeIngestJournal(logId), expectedPrefix, chunkNumbersSeen);
    }

    /**
     * Renders every ingest POST WireMock recorded, with its arrival time and chunk coordinates, so a failure
     * shows which chunks arrived and when rather than only which set was expected. The journal holds only
     * requests that reached WireMock, so a chunk missing from it was either never sent or lost on the way —
     * the dump cannot tell those apart. Entries for other messages are kept: they reveal whether the sender
     * was working at all around the outage.
     */
    private String describeIngestJournal(String logId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/requests")).GET().build();
        JsonNode root = new ObjectMapper().readTree(client.send(request, BodyHandlers.ofString()).body());
        StringBuilder out = new StringBuilder(NL).append("Journal entries returned=")
            .append(root.get("requests").size()).append(" meta=").append(root.get("meta"))
            .append(" journalDisabled=").append(root.get("requestJournalDisabled"))
            .append(NL).append("Ingest journal for logId ").append(logId).append(':');
        for (JsonNode entry : root.get("requests")) {
            JsonNode requestNode = entry.get("request");
            if (!"POST".equals(requestNode.get("method").asText())
                || !"/api/processing/ingest/dataprovider".equals(requestNode.get("url").asText())) {
                continue;
            }
            JsonNode headers = requestNode.get("headers");
            out.append(NL).append("  ").append(text(requestNode, "loggedDateString"))
                .append(" chunk=").append(header(headers, MessageHeaders.NJAMS_CHUNK_NO_HTTP_HEADER))
                .append('/').append(header(headers, MessageHeaders.NJAMS_CHUNKS_HTTP_HEADER))
                .append(" type=").append(header(headers, MessageHeaders.NJAMS_MESSAGETYPE_HTTP_HEADER))
                .append(logId.equals(header(headers, MessageHeaders.NJAMS_LOGID_HTTP_HEADER)) ? "" : " (other message)");
        }
        return out.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "-" : value.asText();
    }

    private static String header(JsonNode headers, String name) {
        return text(headers, name);
    }

    /** The scenario is only meaningful if the message actually fragmented, so guard that before any gap check. */
    private void assertFragmented(Integer declaredTotalChunks) {
        assertTrue("Message never split into multiple chunks — the size settings failed to force fragmentation",
            declaredTotalChunks != null && declaredTotalChunks > 1);
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
