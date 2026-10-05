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
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.JmsCommandClient;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * R2: with {@code startup.failbehavior=reconnect} and the broker unreachable, {@code start()} succeeds and the JMS
 * receiver connects in the background once the broker is back.
 */
public class ReceiverStartupOutageIT {
    private static final String TOXIC = "receiver-startup-down";

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
    public void receiverConnectsInTheBackgroundOnceTheTargetIsBack() throws Exception {
        ClientSettings settings = ReceiverSettings.jms(env);
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "RECONNECT");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, "2000");
        // the receiver's connect starts in the Njams constructor, so the outage must exist before it
        env.toxiproxy().addToxic("jms", TOXIC, "timeout", Map.of("timeout", 1));
        njams = new Njams(Path.of("R2Jms"), "1.0.0", "CommunicationIT", settings);
        assertTrue("start() must succeed with failbehavior=reconnect", njams.start());
        String path = njams.metadata().getClientPath().toString();
        env.toxiproxy().removeToxic("jms", TOXIC);
        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            assertNotNull("receiver did not connect after the target came back",
                client.awaitReply(Command.PING, path, null, Duration.ofSeconds(120)));
            assertEquals(1, env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));
        }
    }
}
