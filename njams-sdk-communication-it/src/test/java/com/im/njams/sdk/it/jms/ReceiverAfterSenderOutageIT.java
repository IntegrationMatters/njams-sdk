package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.JmsCommandClient;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.it.support.SdkThreads;

/**
 * R5: after the sender group recovered from an outage, the JMS receiver ends up with exactly one connection (it is
 * cycled, not duplicated) and keeps handling commands.
 */
public class ReceiverAfterSenderOutageIT {
    private static final String TOXIC = "receiver-after-sender-outage";

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 240000)
    public void receiverHasExactlyOneConnectionAfterTheSenderGroupRecovered() throws Exception {
        njams = new Njams(Path.of("R5Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
        assertTrue(njams.start());
        String path = njams.metadata().getClientPath().toString();
        assertEquals(1, env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));

        env.toxiproxy().addToxic("jms", TOXIC, "timeout", Map.of("timeout", 1));
        assertEquals("the broker must see the connections drop", 0,
            env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("jms", TOXIC);

        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            assertNotNull("receiver did not answer after the recovery",
                client.awaitReply(Command.PING, path, null, Duration.ofSeconds(120)));
        }
        assertEquals("exactly one receiver consumer after the recovery (no leaked extra connection)", 1,
            env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(60)));
        Set<String> cycling = SdkThreads.awaitNone(Duration.ofSeconds(30), "Receiver-Recovery-Cycle-Thread");
        assertTrue("receiver recovery-cycle thread still alive: " + cycling, cycling.isEmpty());
    }
}
