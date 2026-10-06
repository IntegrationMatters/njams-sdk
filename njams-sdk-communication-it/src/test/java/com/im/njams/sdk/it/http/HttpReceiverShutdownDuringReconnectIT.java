package com.im.njams.sdk.it.http;

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
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.it.support.WireMockStubs;

/** R4 (HTTP): {@code stop()} while the SSE receiver is reconnecting returns promptly and leaves no thread behind. */
public class HttpReceiverShutdownDuringReconnectIT {
    private static final String TOXIC = "http-receiver-shutdown-outage";

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
    public void stopDuringReceiverReconnectIsPromptAndLeavesNoThreads() throws Exception {
        Path path = Path.of("R4Http");
        String messageId = "r4-http-ping";
        WireMockStubs.sseStubFor(env, path.toString(), messageId);
        njams = new Njams(path, "1.0.0", "CommunicationIT", ReceiverSettings.http(env));
        assertTrue(njams.start());
        assertTrue("receiver never connected", WireMockJournal.awaitCountHeaderAtLeast(env, "POST",
            WireMockStubs.REPLY_PATH, "njams-reply-for", messageId, 1, Duration.ofSeconds(60)) >= 1);
        env.toxiproxy().addToxic("http", TOXIC, "reset_peer", Map.of("timeout", 0));
        Thread.sleep(DockerEnvironment.OUTAGE_MS);

        long start = System.nanoTime();
        njams.stop();
        long stopMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("stop() took " + stopMs + " ms while the receiver was reconnecting", stopMs < 10_000);

        Set<String> survivors = SdkThreads.awaitNone(Duration.ofSeconds(10), SdkThreads.RECEIVER);
        assertTrue("Receiver-* threads survived stop(): " + survivors, survivors.isEmpty());
        env.toxiproxy().removeToxic("http", TOXIC);
    }
}
