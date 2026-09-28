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

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.Settings;

/**
 * Reads the host ports fabric8's docker-maven-plugin assigned (via {@code target/docker-it.properties}) and
 * exposes typed accessors, plus a per-test reset of Toxiproxy toxics and the WireMock request journal so
 * state from one IT never leaks into the next (Review Focus items 1 and 3).
 */
public class DockerEnvironment extends ExternalResource {

    private static final String ACTIVEMQ_INITIAL_CONTEXT_FACTORY =
        "org.apache.activemq.jndi.ActiveMQInitialContextFactory";

    /**
     * One of ActiveMQ's own default JNDI connection-factory bindings (confirmed against
     * {@code ActiveMQInitialContextFactory}'s default binding names) — no broker-side configuration needed.
     */
    private static final String ACTIVEMQ_CONNECTION_FACTORY_NAME = "ConnectionFactory";

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

    /**
     * Sets the JNDI settings a real {@code Njams} instance needs to actually connect to the ActiveMQ broker this
     * environment runs — the SDK's default {@code JndiJmsFactory} requires {@code PROPERTY_JMS_CONNECTION_FACTORY}
     * to be non-blank or every connect attempt fails with {@code IllegalStateException: Not initialized}
     * regardless of network reachability, which would defeat every JMS scenario in this module. Callers still set
     * {@code PROPERTY_COMMUNICATION} and {@code PROPERTY_JMS_PROVIDER_URL} themselves, since those vary (direct vs.
     * through-proxy) per test.
     */
    public void configureJms(Settings settings) {
        settings.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY, ACTIVEMQ_INITIAL_CONTEXT_FACTORY);
        settings.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ACTIVEMQ_CONNECTION_FACTORY_NAME);
    }

    /**
     * Sets {@code PROPERTY_DISCARD_POLICY} to {@code none} (block until a dispatch-queue slot frees up, rather
     * than drop) so a scenario that asserts on delivery counts is testing outage/recovery behavior, not the
     * unrelated default discard-under-burst behavior — the sender pool's dispatch queue is small (default
     * capacity 8) and a {@code MessageDriver} burst can easily exceed it well before any outage is involved.
     * Do not call this from a scenario whose own purpose is exercising discard-policy-dependent behavior.
     */
    public void disableMessageDiscarding(Settings settings) {
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, "none");
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
        HttpRequest deleteRequests = HttpRequest.newBuilder(URI.create(wireMockAdminUrl() + "/requests"))
            .timeout(Duration.ofSeconds(5))
            .DELETE()
            .build();
        client.send(deleteRequests, BodyHandlers.discarding());
        // Also restore stub mappings to their classpath-loaded baseline: an on-demand mapping a test posts (e.g.
        // HttpRejectAndCongestionIT's 429/413/503 stubs) has a higher priority than the baseline post-ok.json and
        // would otherwise keep matching every subsequent IT's requests in the same Docker session.
        HttpRequest resetMappings = HttpRequest.newBuilder(URI.create(wireMockAdminUrl() + "/mappings/reset"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        client.send(resetMappings, BodyHandlers.discarding());
    }
}
