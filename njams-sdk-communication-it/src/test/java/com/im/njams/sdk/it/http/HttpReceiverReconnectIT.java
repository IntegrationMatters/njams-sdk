package com.im.njams.sdk.it.http;

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
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.it.support.SdkThreads;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.it.support.WireMockStubs;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * R1/R2/R5 (HTTP): the SSE receiver resubscribes after a connection loss, connects in the background when the
 * server was down at startup, and runs at most one recovery cycle after the sender group recovered.
 */
public class HttpReceiverReconnectIT {
    private static final String TOXIC = "http-receiver-outage";

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    private static long subscribes(DockerEnvironment env) throws Exception {
        return WireMockJournal.countMatching(env, "GET", WireMockStubs.SUBSCRIBE_PATH, null);
    }

    /** R1 + R5: outage during runtime; the receiver resubscribes, answers again and leaves one recovery cycle at most. */
    @Test(timeout = 240000)
    public void receiverResubscribesAfterConnectionLossAndAnswersAgain() throws Exception {
        Path path = Path.of("R1Http");
        String messageId = "r1-http-ping";
        WireMockStubs.sseStubFor(env, path.toString(), messageId);
        njams = new Njams(path, "1.0.0", "CommunicationIT", ReceiverSettings.http(env));
        assertTrue(njams.start());
        long replies = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", messageId, 1, Duration.ofSeconds(60));
        assertTrue("no reply before the outage", replies >= 1);
        long subscribesBefore = subscribes(env);

        env.toxiproxy().addToxic("http", TOXIC, "timeout", Map.of("timeout", 1));
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("http", TOXIC);

        long after = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", messageId, replies + 1, Duration.ofSeconds(120));
        assertTrue("receiver did not answer again after the outage, replies " + replies + " -> " + after,
            after > replies);
        assertTrue("subscribe count did not increase after the outage",
            subscribes(env) > subscribesBefore);
        Set<String> cycling = SdkThreads.awaitNone(Duration.ofSeconds(30), "Receiver-Recovery-Cycle-Thread");
        assertTrue("receiver recovery-cycle thread still alive: " + cycling, cycling.isEmpty());
    }

    /** R2: server unreachable at startup with failbehavior=reconnect; the receiver connects once it is back. */
    @Test(timeout = 240000)
    public void receiverConnectsInTheBackgroundOnceTheServerIsBack() throws Exception {
        Path path = Path.of("R2Http");
        String messageId = "r2-http-ping";
        WireMockStubs.sseStubFor(env, path.toString(), messageId);
        ClientSettings settings = ReceiverSettings.http(env);
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "RECONNECT");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, "2000");
        env.toxiproxy().addToxic("http", TOXIC, "timeout", Map.of("timeout", 1));
        njams = new Njams(path, "1.0.0", "CommunicationIT", settings);
        assertTrue("start() must succeed with failbehavior=reconnect", njams.start());
        env.toxiproxy().removeToxic("http", TOXIC);

        long replies = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", messageId, 1, Duration.ofSeconds(120));
        assertTrue("receiver did not connect after the server came back, replies=" + replies, replies >= 1);
    }
}
