package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Map;

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

/** R1: the JMS receiver reconnects on its own after a connection loss and keeps handling commands. */
public class ReceiverReconnectIT {
    private static final String TOXIC = "receiver-outage";

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 180000)
    public void receiverReconnectsAfterConnectionLossAndHandlesCommands() throws Exception {
        njams = new Njams(Path.of("R1Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
        assertTrue(njams.start());
        String path = njams.metadata().getClientPath().toString();
        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            assertNotNull(client.awaitReply(Command.PING, path, null, Duration.ofSeconds(30)));
            env.toxiproxy().addToxic("jms", TOXIC, "timeout", Map.of("timeout", 1));
            assertEquals("the broker must see the receiver's connection drop during the outage", 0,
                env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));
            Thread.sleep(DockerEnvironment.OUTAGE_MS);
            env.toxiproxy().removeToxic("jms", TOXIC);
            assertNotNull("receiver did not recover after the outage",
                client.awaitReply(Command.PING, path, null, Duration.ofSeconds(120)));
            assertEquals("exactly one consumer on the commands topic after the recovery", 1,
                env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));
            assertTrue("more than one receiver reconnector thread alive",
                SdkThreads.alive("Receiver-Sender-Reconnector-Thread").size() <= 1);
        }
    }
}
