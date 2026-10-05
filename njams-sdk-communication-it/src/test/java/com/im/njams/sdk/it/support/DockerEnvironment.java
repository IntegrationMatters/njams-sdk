package com.im.njams.sdk.it.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

import org.junit.rules.ExternalResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

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

    /**
     * How long a scenario keeps a transport outage in force before restoring it. Must outlast the sender's own
     * bounded quick-retry window (about one second), otherwise a message could be saved by local retries alone
     * and never reach the reconnect / discard-policy handling the scenario targets.
     */
    public static final long OUTAGE_MS = 3_000;

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
    public void configureJms(ClientSettings settings) {
        settings.put(NjamsSettings.PROPERTY_JMS_INITIAL_CONTEXT_FACTORY, ACTIVEMQ_INITIAL_CONTEXT_FACTORY);
        settings.put(NjamsSettings.PROPERTY_JMS_CONNECTION_FACTORY, ACTIVEMQ_CONNECTION_FACTORY_NAME);
    }

    /**
     * Pins {@code PROPERTY_DISCARD_POLICY} to {@code none} for a scenario that is not about discard behavior at
     * all (pool bookkeeping, degraded connect): blocking instead of dropping keeps such a scenario's delivery
     * assertions independent of the small dispatch queue. Scenarios that verify connection-problem handling run
     * once per {@link DiscardMode} instead and must not call this.
     */
    public void disableMessageDiscarding(ClientSettings settings) {
        DiscardMode.NONE.apply(settings);
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

    /**
     * Reads ActiveMQ's own {@code CurrentConnectionsCount} broker attribute via Jolokia — confirmed empirically
     * against the running container (not assumed from general ActiveMQ/Jolokia familiarity): the MBean is
     * {@code org.apache.activemq:type=Broker,brokerName=localhost} (the {@code apache/activemq-classic} image's
     * default broker name), and the attribute tracked 0 -> 2 across two independently-opened JMS connections in a
     * manual probe. Jolokia rejects requests whose {@code Origin} header is missing/{@code null} with a 403, so an
     * explicit same-origin value is set here.
     *
     * @return the number of connections the broker currently holds open.
     */
    public int brokerConnectionCount() throws IOException, InterruptedException, URISyntaxException {
        return jolokiaRead("org.apache.activemq:type=Broker,brokerName=localhost", "CurrentConnectionsCount").asInt();
    }

    /**
     * Reads the number of consumers attached to the SDK's commands topic (one per connected JMS receiver).
     * Confirmed empirically (SDK-483 spike): the topic MBean exists only after the topic was first used.
     *
     * @return the consumer count, {@code 0} while the topic's MBean does not exist.
     */
    public int commandsTopicConsumerCount() throws IOException, InterruptedException, URISyntaxException {
        JsonNode value = jolokiaRead("org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Topic,"
            + "destinationName=" + ReceiverSettings.COMMANDS_TOPIC, "ConsumerCount");
        return value == null || value.isNull() ? 0 : value.asInt();
    }

    /**
     * Polls {@link #commandsTopicConsumerCount()} every 500 ms until it equals {@code expected} or {@code timeout}
     * elapsed.
     *
     * @return the last value read.
     */
    public int awaitCommandsTopicConsumerCount(int expected, Duration timeout)
        throws IOException, InterruptedException, URISyntaxException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int last = commandsTopicConsumerCount();
        while (last != expected && System.nanoTime() < deadline) {
            Thread.sleep(500);
            last = commandsTopicConsumerCount();
        }
        return last;
    }

    /**
     * Reads one broker attribute via Jolokia.
     *
     * @return the attribute's value, or {@code null} if the MBean does not exist (yet).
     */
    public JsonNode jolokiaRead(String mbean, String attribute)
        throws IOException, InterruptedException, URISyntaxException {
        URI jolokiaUri = URI.create(jolokiaUrl());
        String credentials = Base64.getEncoder().encodeToString(jolokiaUri.getUserInfo().getBytes());
        URI requestUri = new URI(jolokiaUri.getScheme(), null, jolokiaUri.getHost(), jolokiaUri.getPort(),
            jolokiaUri.getPath(), null, null);
        String origin = jolokiaUri.getScheme() + "://" + jolokiaUri.getHost() + ":" + jolokiaUri.getPort();

        ObjectMapper mapper = new ObjectMapper();
        String requestBody = mapper.writeValueAsString(
            Map.of("type", "read", "mbean", mbean, "attribute", attribute));
        HttpRequest request = HttpRequest.newBuilder(requestUri)
            .header("Authorization", "Basic " + credentials)
            .header("Origin", origin)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        String body = HttpClient.newHttpClient().send(request, BodyHandlers.ofString()).body();
        JsonNode root = mapper.readTree(body);
        return root.has("value") ? root.get("value") : null;
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
