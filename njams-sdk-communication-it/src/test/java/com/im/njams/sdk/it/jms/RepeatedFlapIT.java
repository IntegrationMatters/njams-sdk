package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
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

public class RepeatedFlapIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 60000)
    public void repeatedFlappingDoesNotAccumulateThreads() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);

        Njams njams = new Njams(Path.of("RepeatedFlapIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 50, 1);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int baselineThreadCount = threadBean.getThreadCount();

        for (int i = 0; i < 10; i++) {
            env.toxiproxy().addToxic("jms", "flap", "timeout", Map.of("timeout", 1));
            Thread background = new Thread(() -> {
                try {
                    MessageDriver.run(model, 3, 50, 2);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            background.start();
            Thread.sleep(200);
            env.toxiproxy().removeToxic("jms", "flap");
            background.join(10000);
        }

        // Allow reconnect threads from the last cycle a moment to actually terminate.
        Thread.sleep(1000);
        int finalThreadCount = threadBean.getThreadCount();

        njams.stop();

        assertTrue("Thread count grew from " + baselineThreadCount + " to " + finalThreadCount
            + " across 10 flap cycles — suspect a reconnect-thread leak",
            finalThreadCount <= baselineThreadCount + 2); // small slack for JIT/GC housekeeping threads
    }
}
