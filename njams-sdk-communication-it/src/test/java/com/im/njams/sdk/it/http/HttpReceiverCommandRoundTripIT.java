package com.im.njams.sdk.it.http;

import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.ReceiverSettings;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.it.support.WireMockStubs;

/** R3 (HTTP): a command delivered on the SSE stream is handled and answered with a reply POST. */
public class HttpReceiverCommandRoundTripIT {
    private static final String MESSAGE_ID = "r3-http-ping";

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
    public void commandOnTheSseStreamIsAnsweredWithAReplyPost() throws Exception {
        Path path = Path.of("R3Http");
        WireMockStubs.sseStubFor(env, path.toString(), MESSAGE_ID);
        njams = new Njams(path, "1.0.0", "CommunicationIT", ReceiverSettings.http(env));
        assertTrue(njams.start());
        long replies = WireMockJournal.awaitCountHeaderAtLeast(env, "POST", WireMockStubs.REPLY_PATH,
            "njams-reply-for", MESSAGE_ID, 1, Duration.ofSeconds(60));
        assertTrue("no reply POST for the command, count=" + replies, replies >= 1);
    }
}
