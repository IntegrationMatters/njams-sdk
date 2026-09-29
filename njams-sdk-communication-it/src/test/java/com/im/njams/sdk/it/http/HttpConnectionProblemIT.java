package com.im.njams.sdk.it.http;

import static com.im.njams.sdk.it.support.WireMockJournal.INGEST_PATH;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.it.harness.FixedProcessModel;
import com.im.njams.sdk.it.harness.MessageDriver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.it.support.WireMockJournal;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

public class HttpConnectionProblemIT {

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private Njams njams;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 30000)
    public void transportLevelOutageUsesTheSameRetireReconnectPathAsApplicationLevel503() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        env.disableMessageDiscarding(settings);

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
        Thread.sleep(500);
        env.toxiproxy().removeToxic("http", "transport-down");
        background.join(20000);

        assertTrue("The job driven during the outage must have been created before the driver thread joined",
            logIds.size() == 2);
        String duringOutageLogId = logIds.get(1);
        long deliveredAfterRecovery = WireMockJournal.awaitCount(env, "POST", INGEST_PATH, duringOutageLogId,
            c -> c >= 1, Duration.ofSeconds(15));
        assertTrue("The job driven during the transport-level outage must still be delivered once it clears",
            deliveredAfterRecovery >= 1);
    }
}
