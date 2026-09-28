package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.it.support.ToxiproxyControl;

public class HttpThroughProxySmokeIT {

    private int proxyPort;

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("target/docker-it.properties")) {
            props.load(in);
        }
        int controlPort = Integer.parseInt(props.getProperty("+toxiproxy.control"));
        proxyPort = Integer.parseInt(props.getProperty("+toxiproxy.http"));

        ToxiproxyControl toxiproxy = new ToxiproxyControl(controlPort);
        toxiproxy.createProxy("http", "0.0.0.0:20001", "wiremock:8080");
    }

    @Test
    public void postThroughTheProxyReachesTheStub() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + proxyPort + "/dataprovider"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
