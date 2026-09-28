package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

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

public class DegradedConnectIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null) {
            njams.stop();
        }
    }

    @Test
    public void otherPoolTrafficStaysResponsiveWhileOneConnectIsSlow() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "4");
        env.configureJms(settings);

        njams = new Njams(Path.of("DegradedConnectIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        // Warm the pool with a healthy connection first, then add latency for any *new* connection attempt only
        // (established traffic through the existing connections is unaffected by this toxic).
        MessageDriver.run(model, 5, 100, 1);
        env.toxiproxy().addToxic("jms", "slow-connect", "latency", Map.of("latency", 15000, "jitter", 0));

        long start = System.currentTimeMillis();
        // Concurrency forces the pool to grow, so at least one worker hits the slow-connect path while the
        // others should still be served promptly by already-connected senders.
        MessageDriver.run(model, 20, 100, 8);
        long elapsedMs = System.currentTimeMillis() - start;

        // Before the B2 fix this would have taken >15s because acquire()/release() serialized behind the one
        // slow connect; after the fix it should complete in well under that.
        assertTrue("Pool traffic was stalled by one slow connect (took " + elapsedMs + "ms)", elapsedMs < 8000);
    }
}
