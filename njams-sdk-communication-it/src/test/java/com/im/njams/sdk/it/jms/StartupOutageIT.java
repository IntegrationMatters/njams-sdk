package com.im.njams.sdk.it.jms;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.SdkThreads;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Startup with {@code startup.failbehavior=fail} and the broker unreachable: the SDK must shut down fully. This is
 * independent of the discard policy, so it is not repeated per mode. The reconnect counterpart is
 * {@link StartupReconnectIT}.
 */
public class StartupOutageIT {

    /** Longer than the sender's reconnect interval, so a reconnect that wrongly kept running would show up. */
    private static final long OBSERVATION_MS = 4_000;

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 60000)
    public void startFailsAndLeavesTheSdkFullyShutDown() throws Exception {
        env.toxiproxy().addToxic("jms", "startup-down", "timeout", Map.of("timeout", 1));

        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "FAIL");
        env.configureJms(settings);

        njams = new Njams(Path.of("StartupOutageIT"), "1.0.0", "CommunicationIT", settings);
        boolean started = njams.start();

        assertFalse("start() must report failure when the broker was never reachable at startup", started);
        assertFalse("A failed start must leave the instance inactive", njams.isStarted());
        assertNoSdkThreadsAlive("after the failed start");

        // Nothing may reconnect on its own once the broker is reachable again: the broker must not see a single
        // new connection, and no SDK thread may come back. The broker's own count is the observable because a
        // JMS connection attempt leaves no other trace outside the SDK.
        int baselineConnections = settledBrokerConnectionCount();
        env.toxiproxy().removeToxic("jms", "startup-down");
        Thread.sleep(OBSERVATION_MS);

        assertEquals("A failed start must not reconnect once the broker is back", baselineConnections,
            env.brokerConnectionCount());
        assertNoSdkThreadsAlive("after the broker became reachable again");
    }

    private static void assertNoSdkThreadsAlive(String when) throws InterruptedException {
        Set<String> survivors = SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.SENDER_STARTUP,
            SdkThreads.SENDER_RECONNECTOR, SdkThreads.RECEIVER);
        assertTrue("SDK threads survived the failed start " + when + ": " + survivors, survivors.isEmpty());
    }

    /** Waits until the broker's connection count has been unchanged for two seconds (connections from the outage
     * itself, e.g. half-open ones from the proxy, are still closing right after the failed start). */
    private int settledBrokerConnectionCount() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        int last = env.brokerConnectionCount();
        long stableSince = System.nanoTime();
        while (System.nanoTime() < deadline) {
            Thread.sleep(500);
            int current = env.brokerConnectionCount();
            if (current != last) {
                last = current;
                stableSince = System.nanoTime();
            } else if (System.nanoTime() - stableSince >= Duration.ofSeconds(2).toNanos()) {
                return current;
            }
        }
        return last;
    }
}
