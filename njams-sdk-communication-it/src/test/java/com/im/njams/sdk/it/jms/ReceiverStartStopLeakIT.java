package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.it.support.SdkThreads;

/** R7: repeated start/stop cycles leave no JMS receiver thread and no consumer on the commands topic behind. */
public class ReceiverStartStopLeakIT {
    private static final int CYCLES = 10;

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Test(timeout = 240000)
    public void repeatedStartStopLeavesNoReceiverThreadsOrConsumers() throws Exception {
        for (int i = 0; i < CYCLES; i++) {
            Njams njams = new Njams(Path.of("R7Jms-" + i), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
            assertTrue("cycle " + i + ": start() failed", njams.start());
            njams.stop();
        }
        Set<String> survivors = SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.RECEIVER);
        assertTrue("Receiver-* threads survived " + CYCLES + " start/stop cycles: " + survivors,
            survivors.isEmpty());
        assertEquals("consumers left on the commands topic", 0,
            env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));
    }
}
