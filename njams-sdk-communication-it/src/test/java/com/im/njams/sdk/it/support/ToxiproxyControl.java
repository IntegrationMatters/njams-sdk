package com.im.njams.sdk.it.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin wrapper over Toxiproxy's REST API (https://github.com/Shopify/toxiproxy#http-api). */
public class ToxiproxyControl {

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public ToxiproxyControl(int controlPort) {
        this.baseUrl = "http://localhost:" + controlPort;
    }

    public void createProxy(String name, String listen, String upstream) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("name", name, "listen", listen, "upstream", upstream);
        send("POST", "/proxies", body);
    }

    public void addToxic(String proxyName, String toxicName, String type, Map<String, Object> attributes)
        throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("name", toxicName, "type", type, "attributes", attributes);
        send("POST", "/proxies/" + proxyName + "/toxics", body);
    }

    public void removeToxic(String proxyName, String toxicName) throws IOException, InterruptedException {
        send("DELETE", "/proxies/" + proxyName + "/toxics/" + toxicName, null);
    }

    /** Removes every toxic from every proxy. Call this in an {@code @After} to avoid leaking state between tests. */
    public void resetAll() throws IOException, InterruptedException {
        send("POST", "/reset", null);
    }

    private void send(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(5));
        if (body != null) {
            builder.method(method, BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .header("Content-Type", "application/json");
        } else {
            builder.method(method, BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(builder.build(), BodyHandlers.ofString());
        if (response.statusCode() >= 300 && response.statusCode() != 409) {
            // 409 = proxy/toxic already exists; treated as idempotent, not an error, for repeated test setup.
            throw new IOException("Toxiproxy call failed: " + method + " " + path + " -> " + response.statusCode()
                + " " + response.body());
        }
    }
}
