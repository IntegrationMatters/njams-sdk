package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class HttpStartupOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void startFailsOnTransportLevelOutage() throws Exception {
        env.toxiproxy().addToxic("http", "startup-down", "timeout", Map.of("timeout", 1));
        njams = startWithFailBehavior();
        assertFalse(njams.start());
    }

    @Test
    public void startFailsWhenHeadReturns404() throws Exception {
        loadOnDemandMapping("head-not-found.json");
        njams = startWithFailBehavior();
        assertFalse(njams.start());
    }

    private Njams startWithFailBehavior() {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");
        return new Njams(Path.of("HttpStartupOutageIT"), "1.0.0", "CommunicationIT", settings);
    }

    private void loadOnDemandMapping(String classpathResource) throws IOException, InterruptedException {
        String body;
        try (var in = getClass().getClassLoader().getResourceAsStream("wiremock/on-demand/" + classpathResource)) {
            body = new String(in.readAllBytes());
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.wireMockAdminUrl() + "/mappings"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        client.send(request, BodyHandlers.discarding());
    }
}
