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

public class HttpShutdownDuringOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 20000)
    public void stopCompletesPromptlyEvenWhileReconnecting() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");

        Njams njams = new Njams(Path.of("HttpShutdownDuringOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 100, 1);

        env.toxiproxy().addToxic("http", "shutdown-outage", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 5, 100, 1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(300); // let the reconnect loop actually start

        // The @Test(timeout=...) above is the real assertion: stop() must not hang waiting on the reconnect loop.
        njams.stop();
        background.join(5000);
    }
}
