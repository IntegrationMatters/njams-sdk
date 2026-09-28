package com.im.njams.sdk.it.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Properties;

import org.junit.rules.ExternalResource;

/**
 * Reads the host ports fabric8's docker-maven-plugin assigned (via {@code target/docker-it.properties}) and
 * exposes typed accessors, plus a per-test reset of Toxiproxy toxics and the WireMock request journal so
 * state from one IT never leaks into the next (Review Focus items 1 and 3).
 */
public class DockerEnvironment extends ExternalResource {

    private final Properties props = new Properties();
    private final ToxiproxyControl toxiproxy;
    private boolean proxiesCreated;

    public DockerEnvironment() {
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("docker-it.properties not found — is this running under -Pdocker-it "
                + "after docker:start has run?", e);
        }
        this.toxiproxy = new ToxiproxyControl(port("+toxiproxy.control"));
    }

    @Override
    protected void before() throws Throwable {
        if (!proxiesCreated) {
            toxiproxy.createProxy("jms", "0.0.0.0:20000", "activemq:61616");
            toxiproxy.createProxy("http", "0.0.0.0:20001", "wiremock:8080");
            proxiesCreated = true;
        }
    }

    @Override
    protected void after() {
        try {
            toxiproxy.resetAll();
            resetWireMock();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to reset Docker environment state between tests", e);
        }
    }

    public ToxiproxyControl toxiproxy() {
        return toxiproxy;
    }

    public String jmsUrlDirect() {
        return "tcp://localhost:" + port("+activemq.openwire");
    }

    public String jmsUrlThroughProxy() {
        return "tcp://localhost:" + port("+toxiproxy.jms");
    }

    public String httpBaseUrlDirect() {
        return "http://localhost:" + port("+wiremock.http");
    }

    public String httpBaseUrlThroughProxy() {
        return "http://localhost:" + port("+toxiproxy.http");
    }

    public String jolokiaUrl() {
        return "http://admin:admin@localhost:" + port("+activemq.console") + "/api/jolokia";
    }

    public String wireMockAdminUrl() {
        return httpBaseUrlDirect() + "/__admin";
    }

    private int port(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing docker-it.properties key: " + key);
        }
        return Integer.parseInt(value);
    }

    private void resetWireMock() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(wireMockAdminUrl() + "/requests"))
            .timeout(Duration.ofSeconds(5))
            .DELETE()
            .build();
        client.send(request, BodyHandlers.discarding());
    }
}
