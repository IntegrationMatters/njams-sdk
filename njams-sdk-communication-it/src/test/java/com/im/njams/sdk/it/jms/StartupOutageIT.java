package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertFalse;

import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.settings.Settings;

public class StartupOutageIT {

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
    public void startFailsWhenTheBrokerIsUnreachableAtStartup() throws Exception {
        env.toxiproxy().addToxic("jms", "startup-down", "timeout", Map.of("timeout", 1));

        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");
        env.configureJms(settings);

        njams = new Njams(Path.of("StartupOutageIT"), "1.0.0", "CommunicationIT", settings);
        boolean started = njams.start();

        assertFalse("start() must report failure when the broker was never reachable at startup", started);
    }
}
