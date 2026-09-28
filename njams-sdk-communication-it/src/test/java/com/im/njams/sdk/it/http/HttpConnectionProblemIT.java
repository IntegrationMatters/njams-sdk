package com.im.njams.sdk.it.http;

import java.util.Map;

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

public class HttpConnectionProblemIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 30000)
    public void transportLevelOutageUsesTheSameRetireReconnectPathAsApplicationLevel503() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");

        Njams njams = new Njams(Path.of("HttpConnectionProblemIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 1, 100, 1);

        env.toxiproxy().addToxic("http", "transport-down", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 1, 100, 1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(500);
        env.toxiproxy().removeToxic("http", "transport-down");
        background.join(20000);

        njams.stop();
    }
}
