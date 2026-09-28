package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.it.support.DockerEnvironment;

public class HttpThroughProxySmokeIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test
    public void postThroughTheProxyReachesTheStub() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(env.httpBaseUrlThroughProxy() + "/dataprovider"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
