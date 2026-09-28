package com.im.njams.sdk.it.support;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import javax.jms.Connection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Rule;
import org.junit.Test;

public class DockerEnvironmentSmokeIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void portsAreDynamicallyAssignedNotFixedDefaults() {
        // Review Focus #2: a hardcoded default (61616, 8080, 8474) would defeat the purpose of reading
        // fabric8's generated properties file at all.
        assertNotEquals("tcp://localhost:61616", env.jmsUrlThroughProxy());
        assertNotEquals("http://localhost:8080", env.httpBaseUrlThroughProxy());
    }

    @Test
    public void toxicOnOneProxyDoesNotAffectTheOther() throws Exception {
        // Review Focus #5: a toxic on the "jms" proxy must not touch the "http" proxy sharing the same
        // Toxiproxy container.
        env.toxiproxy().addToxic("jms", "cross-check", "timeout", Map.of("timeout", 1));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
            URI.create(env.httpBaseUrlThroughProxy() + "/api/processing/ingest/dataprovider"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        assertTrue(client.send(request, BodyHandlers.ofString()).statusCode() == 200);
    }

    @Test
    public void resetAllClearsToxicsBetweenTests() throws Exception {
        // Review Focus #1: this test intentionally runs after the one above, relying on DockerEnvironment's
        // @Rule-driven after() having already reset the "jms" proxy's toxic — if it hadn't, this connection
        // would also fail.
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(env.jmsUrlThroughProxy());
        try (Connection connection = factory.createConnection()) {
            connection.start();
        }
    }
}
