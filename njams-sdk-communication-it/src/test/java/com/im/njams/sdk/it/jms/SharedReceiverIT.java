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
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.JmsCommandClient;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.settings.ClientSettings;

/** R6: several clients sharing one JMS receiver. */
public class SharedReceiverIT {
    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams a;
    private Njams b;
    private Njams c;

    @After
    public void tearDown() {
        for (Njams n : new Njams[] { a, b, c }) {
            if (n != null && n.isStarted()) {
                n.stop();
            }
        }
    }

    private Njams client(String name) {
        ClientSettings settings = ReceiverSettings.jms(env);
        settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        return new Njams(Path.of("R6Jms", name), "1.0.0", "CommunicationIT", settings);
    }

    private static String path(Njams njams) {
        return njams.metadata().getClientPath().toString();
    }

    @Test(timeout = 240000)
    public void sharedReceiverRoutesCommandsAndReleasesTheConsumerWithTheLastInstance() throws Exception {
        a = client("A");
        b = client("B");
        assertTrue(a.start());
        assertTrue(b.start());
        try (JmsCommandClient client = new JmsCommandClient(env, ReceiverSettings.COMMANDS_TOPIC)) {
            // each instance is addressed by its exact path and answers for itself
            Instruction replyA = client.awaitReply(Command.PING, path(a), null, Duration.ofSeconds(30));
            assertNotNull(replyA);
            assertEquals(a.metadata().getClientSessionId(), replyA.getResponse().getParameters().get("clientId"));
            Instruction replyB = client.awaitReply(Command.PING, path(b), null, Duration.ofSeconds(30));
            assertNotNull(replyB);
            assertEquals(b.metadata().getClientSessionId(), replyB.getResponse().getParameters().get("clientId"));
            assertEquals("one shared consumer for both instances", 1,
                env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));

            // an ancestor path matches the selector but addresses no instance
            Instruction notFound = client.awaitReply(Command.PING, Path.of("R6Jms").toString(), null,
                Duration.ofSeconds(30));
            assertNotNull(notFound);
            assertEquals(99, notFound.getResponse().getResultCode());

            // stopping one instance leaves the shared receiver connected for the other one
            a.stop();
            assertEquals(1, env.awaitCommandsTopicConsumerCount(1, Duration.ofSeconds(30)));
            assertNotNull(client.awaitReply(Command.PING, path(b), null, Duration.ofSeconds(30)));

            // the last instance releases the consumer
            b.stop();
            assertEquals(0, env.awaitCommandsTopicConsumerCount(0, Duration.ofSeconds(30)));

            // a restart builds a fresh shared receiver
            c = client("C");
            assertTrue(c.start());
            assertNotNull(client.awaitReply(Command.PING, path(c), null, Duration.ofSeconds(30)));
        }
    }
}
