package com.im.njams.sdk.it.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Registers and removes WireMock mappings that serve the HTTP receiver's Server-Sent-Events stream. */
public final class WireMockStubs {

    /** The path the HTTP receiver subscribes to. */
    public static final String SUBSCRIBE_PATH = "/api/httpcommunication/subscribe";

    /** The path the HTTP receiver posts its replies to. */
    public static final String REPLY_PATH = "/api/httpcommunication/reply";

    private static final int PADDING_LINES = 40;

    private WireMockStubs() {
    }

    /**
     * Registers a subscribe stub that delivers one {@code PING} command for the given client and keeps the stream
     * open for about 20 s (the receiver then resubscribes and receives the same event again). The event is followed
     * by comment lines so that the first chunk of the dribbled response already contains the complete event (an
     * SSE event is only dispatched at its terminating blank line) — verified against the module's WireMock image.
     *
     * @param clientPath the receiver path of the client, i.e. {@code njams.metadata().getClientPath().toString()}.
     * @param messageId  the id of the command; the reply carries it in its {@code njams-reply-for} header.
     * @return the id of the created mapping, for {@link #remove(DockerEnvironment, String)}.
     */
    public static String sseStubFor(DockerEnvironment env, String clientPath, String messageId)
        throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        String eventHeaders = mapper.writeValueAsString(
            Map.of("njams-receiver", clientPath, "njams-message-id", messageId, "njams-content", "json"));
        StringBuilder sse = new StringBuilder("id: 1\nevent: ").append(eventHeaders)
            .append("\ndata: {\"request\":{\"command\":\"Ping\"}}\n\n");
        for (int i = 0; i < PADDING_LINES; i++) {
            sse.append(": keepalive padding padding padding padding padding padding padding padding padding\n");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("headers", Map.of("Content-Type", "text/event-stream"));
        response.put("body", sse.toString());
        response.put("chunkedDribbleDelay", Map.of("numberOfChunks", 20, "totalDuration", 20000));
        String mapping = mapper.writeValueAsString(
            Map.of("request", Map.of("method", "GET", "urlPath", SUBSCRIBE_PATH), "response", response));
        HttpResponse<String> created = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapping)).build(),
            HttpResponse.BodyHandlers.ofString());
        if (created.statusCode() != 201) {
            throw new IOException("Could not create the SSE stub: " + created.statusCode() + " " + created.body());
        }
        return mapper.readTree(created.body()).get("id").asText();
    }

    /** Removes a mapping created by {@link #sseStubFor}. */
    public static void remove(DockerEnvironment env, String mappingId) throws IOException, InterruptedException {
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings/" + mappingId)).DELETE().build(),
            HttpResponse.BodyHandlers.discarding());
    }
}
