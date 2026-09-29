package com.im.njams.sdk.it.jms;

import java.util.Map;

import org.junit.After;
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

public class ShutdownDuringOutageIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    /**
     * Safety net only — the test's own point is that {@code njams.stop()} inline, inside the
     * {@code @Test(timeout=...)} bound, completes promptly under outage; {@code @After} runs outside that bound,
     * so it must not become the primary way this instance is stopped. It only catches the case where the test
     * fails before reaching its own inline {@code stop()} call, so a failed run never leaves a live instance
     * reconnecting for the rest of the suite.
     */
    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 20000)
    public void stopCompletesPromptlyEvenWhileReconnecting() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);

        njams = new Njams(Path.of("ShutdownDuringOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 100, 1);

        env.toxiproxy().addToxic("jms", "shutdown-outage", "timeout", Map.of("timeout", 1));
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
