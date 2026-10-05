package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.JmsCommandClient;
import com.im.njams.sdk.it.support.ReceiverSettings;

/** R3: a server command reaches the client over JMS and its reply is delivered. */
public class JmsCommandRoundTripIT {
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
    public void pingIsAnsweredWithPong() throws Exception {
        njams = new Njams(Path.of("R3Jms"), "1.0.0", "CommunicationIT", ReceiverSettings.jms(env));
        assertTrue(njams.start());
        String path = njams.metadata().getClientPath().toString();
        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            Instruction reply = client.awaitReply(Command.PING, path, null, Duration.ofSeconds(30));
            assertNotNull("no reply to PING within 30 s", reply);
            assertEquals(0, reply.getResponse().getResultCode());
            assertEquals("Pong", reply.getResponse().getResultMessage());
            assertEquals(njams.metadata().getClientSessionId(), reply.getResponse().getParameters().get("clientId"));
        }
    }
}
