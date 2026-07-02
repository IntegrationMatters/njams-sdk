package com.im.njams.sdk.communication.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.rules.ExternalResource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Minimal in-process nJAMS ingest endpoint backed by the JDK HttpServer. Records POSTed bodies and headers. */
public class IngestHttpServer extends ExternalResource {

    public static final String SUFFIX = "testdp";
    private static final String INGEST_PATH = "/api/processing/ingest/" + SUFFIX;
    private static final String VERSION_PATH = "/api/public/version";

    private HttpServer server;
    private int port;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();

    public String baseUrl() {
        return "http://localhost:" + port + "/";
    }

    public String dataproviderSuffix() {
        return SUFFIX;
    }

    public List<String> receivedBodies() {
        return bodies;
    }

    public List<Map<String, String>> receivedHeaders() {
        return headers;
    }

    public int postCount() {
        return bodies.size();
    }

    public void startServer() throws IOException {
        // reuse the previously assigned port on restart; 0 lets the OS pick on first start
        server = HttpServer.create(new InetSocketAddress(port), 0);
        port = server.getAddress().getPort();
        server.createContext(INGEST_PATH, this::handleIngest);
        server.createContext(VERSION_PATH, this::handleVersion);
        server.setExecutor(null);
        server.start();
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleIngest(HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            final byte[] raw = readAll(exchange.getRequestBody());
            bodies.add(new String(raw, StandardCharsets.UTF_8));
            final java.util.HashMap<String, String> h = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.isEmpty() ? "" : v.get(0)));
            headers.add(h);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    private void handleVersion(HttpExchange exchange) throws IOException {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    protected void before() throws Throwable {
        startServer();
    }

    @Override
    protected void after() {
        stopServer();
    }
}
