package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.it.support.SdkThreads;

/** R4: {@code stop()} while the JMS receiver is reconnecting returns promptly and leaves nothing behind. */
public class ReceiverShutdownDuringReconnectIT {
    private static final String TOXIC = "receiver-shutdown-outage";

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 120000)
    public void stopDuringReceiverReconnectIsPromptAndLeavesNoThreadsOrConsumers() throws Exception {
        njams = new Njams(Path.of("R4Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
        assertTrue(njams.start());
        assertEquals(1, env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));
        env.toxiproxy().addToxic("jms", TOXIC, "timeout", Map.of("timeout", 1));
        assertEquals(0, env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));
        assertTrue("the receiver never started reconnecting",
            !SdkThreads.awaitAny(Duration.ofSeconds(60), "Receiver-Sender-Reconnector-Thread").isEmpty());

        long start = System.nanoTime();
        njams.stop();
        long stopMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("stop() took " + stopMs + " ms while the receiver was reconnecting", stopMs < 10_000);

        Set<String> survivors = SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.RECEIVER);
        assertTrue("Receiver-* threads survived stop(): " + survivors, survivors.isEmpty());
        env.toxiproxy().removeToxic("jms", TOXIC);
        assertEquals("no consumer may stay attached after stop()", 0,
            env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));
    }
}
