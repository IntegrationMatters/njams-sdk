package com.im.njams.sdk.it.http;

import static com.im.njams.sdk.it.support.WireMockJournal.INGEST_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.DiscardObserver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * Scenario 9b, once per discard mode: a transport-level outage (HEAD and POST both unreachable) is a connection
 * problem. {@code none} holds the job and delivers it after recovery; {@code onconnectionloss} and {@code discard}
 * drop it while the group reconnects and count the discard.
 */
@RunWith(Parameterized.class)
public class HttpConnectionProblemIT {

    @Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return Arrays.stream(DiscardMode.values()).map(m -> new Object[] { m }).collect(Collectors.toList());
    }

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Rule
    public DiscardObserver discards = new DiscardObserver();

    private final DiscardMode mode;
    private Njams njams;

    public HttpConnectionProblemIT(DiscardMode mode) {
        this.mode = mode;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 60000)
    public void transportLevelOutageUsesTheSameRetireReconnectPathAsApplicationLevel503() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        mode.apply(settings);

        njams = new Njams(Path.of("HttpConnectionProblemIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        List<String> logIds = new ArrayList<>(MessageDriver.run(model, 1, 100, 1));
        String beforeOutageLogId = logIds.get(0);
        long delivered = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, beforeOutageLogId, c -> c >= 1,
            Duration.ofSeconds(10));
        assertTrue("The warm-up job must be delivered before the outage starts", delivered >= 1);

        env.toxiproxy().addToxic("http", "transport-down", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                logIds.addAll(MessageDriver.run(model, 1, 100, 1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("http", "transport-down");
        background.join(20000);

        assertTrue("The job driven during the outage must have been created before the driver thread joined",
            logIds.size() == 2);
        String duringOutageLogId = logIds.get(1);
        long deliveredAfterRecovery = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, duringOutageLogId,
            c -> c >= 1, Duration.ofSeconds(mode.holdsMessages() ? 15 : 5));
        if (mode.holdsMessages()) {
            assertTrue("The job driven during the transport-level outage must still be delivered once it clears",
                deliveredAfterRecovery >= 1);
            assertEquals("Mode none must never discard", 0, discards.count());
        } else {
            // The toxic only blocks the response direction, so the request itself can still reach the server on
            // every quick retry (the ambiguous-outcome case); the client nevertheless gave up on the job.
            assertTrue("The job must be counted as discarded under " + mode, discards.count() >= 1);
            assertTrue("A dropped job may be attempted only within the quick-retry window, observed "
                + WireMockJournal.countMatching(env, "POST", INGEST_PATH, duringOutageLogId) + " POSTs",
                WireMockJournal.countMatching(env, "POST", INGEST_PATH, duringOutageLogId) <= 4);
        }
    }
}
