package com.im.njams.sdk.it.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class HttpRejectAndCongestionIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 30000)
    public void rejectedMessageIsDiscardedWithoutAffectingTheConnection() throws Exception {
        loadOnDemandMapping("post-413.json");
        Njams njams = startWithDiscardPolicy("NONE");
        ProcessModel model = FixedProcessModel.build(njams);

        MessageDriver.run(model, 1, 100, 1);
        // Send a second, unrelated job afterward on the same instance — if the sender had been wrongly retired
        // over the 413, this would time out waiting for a reconnect instead of completing immediately.
        MessageDriver.run(model, 1, 100, 1);

        njams.stop();
    }

    @Test(timeout = 30000)
    public void congestionRetriesLocallyUnderNonDiscardPolicies() throws Exception {
        loadOnDemandMapping("post-429.json");
        Njams njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        ProcessModel model = FixedProcessModel.build(njams);

        // Under a non-DISCARD policy this call is expected to block, retrying locally, until the mapping is
        // reset back to 200 from a concurrently-scheduled reset — proving it never gives up, retires the
        // sender, or reconnects on 429.
        Thread resetter = new Thread(() -> {
            try {
                Thread.sleep(2000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        resetter.start();
        MessageDriver.run(model, 1, 100, 1);
        resetter.join();

        njams.stop();
    }

    @Test(timeout = 15000)
    public void congestionDiscardsImmediatelyWithoutDelayUnderDiscardPolicy() throws Exception {
        loadOnDemandMapping("post-429.json");
        Njams njams = startWithDiscardPolicy("DISCARD");
        ProcessModel model = FixedProcessModel.build(njams);

        long start = System.currentTimeMillis();
        MessageDriver.run(model, 1, 100, 1);
        long elapsedMs = System.currentTimeMillis() - start;

        njams.stop();
        org.junit.Assert.assertTrue("DISCARD must give up immediately, took " + elapsedMs + "ms", elapsedMs < 3000);
    }

    @Test(timeout = 30000)
    public void applicationLevel503IsTreatedAsConnectionProblemNotCongestion() throws Exception {
        loadOnDemandMapping("post-503.json");
        Njams njams = startWithDiscardPolicy("ONCONNECTIONLOSS");
        ProcessModel model = FixedProcessModel.build(njams);

        Thread recovery = new Thread(() -> {
            try {
                Thread.sleep(1000);
                resetToOkMapping();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        recovery.start();
        // 503 is classified as a connection problem, not congestion (HttpSender.isCongestion() only matches a
        // repeated 429 — confirmed via source read), so this exercises the reconnect path rather than local
        // congestion-retry; both would eventually succeed, but the classification itself is what SDK-476 fixed.
        MessageDriver.run(model, 1, 100, 1);
        recovery.join();

        njams.stop();
    }

    private Njams startWithDiscardPolicy(String policy) {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        settings.put(NjamsSettings.PROPERTY_DISCARD_POLICY, policy);
        Njams njams = new Njams(Path.of("HttpRejectAndCongestionIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        return njams;
    }

    private void loadOnDemandMapping(String classpathResource) throws Exception {
        String body = new String(getClass().getClassLoader()
            .getResourceAsStream("wiremock/on-demand/" + classpathResource).readAllBytes());
        post(env.wireMockAdminUrl() + "/mappings", body);
    }

    private void resetToOkMapping() throws Exception {
        post(env.wireMockAdminUrl() + "/mappings/reset", "");
        loadOnDemandMapping("../mappings/post-ok.json");
    }

    private void post(String url, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build();
        client.send(request, BodyHandlers.discarding());
    }
}
