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
 * R1/R2 (HTTP): the SSE receiver reconnects after a connection loss and connects in the background when the
 * server was down at startup. (R5, the cycle after a sender-group recovery, has no HTTP counterpart: the HTTP
 * sender keeps no connection, so its group only fails and recovers while jobs are being sent.)
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

    /** R1: outage during runtime; the SDK's reconnect loop starts and the receiver then answers a new command. */
    @Test(timeout = 240000)
    public void receiverResubscribesAfterConnectionLossAndAnswersAgain() throws Exception {
        Path path = Path.of("R1Http");
        String messageId = "r1-http-ping";
        String firstStub = WireMockStubs.sseStubFor(env, path.toString(), messageId);
        njams = new Njams(path, "1.0.0", "CommunicationIT", ReceiverSettings.http(env));
        assertTrue(njams.start());
        long replies = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", messageId, 1, Duration.ofSeconds(60));
        assertTrue("no reply before the outage", replies >= 1);
        long subscribesBefore = subscribes(env);

        // A reset (not a silent timeout) so the stream fails visibly. Whether the SSE client library or the SDK's own
        // reconnect loop re-establishes the stream is not observable here; the test proves the receiver recovers.
        env.toxiproxy().addToxic("http", TOXIC, "reset_peer", Map.of("timeout", 0));
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("http", TOXIC);

        // a command with a new id, served only after the outage: its reply cannot stem from the pre-outage stream
        WireMockStubs.remove(env, firstStub);
        String secondId = "r1-http-ping-2";
        WireMockStubs.sseStubFor(env, path.toString(), secondId);
        long after = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", secondId, 1, Duration.ofSeconds(120));
        assertTrue("receiver did not answer a new command after the outage, replies=" + after, after >= 1);
        assertTrue("subscribe count did not increase after the outage", subscribes(env) > subscribesBefore);
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
