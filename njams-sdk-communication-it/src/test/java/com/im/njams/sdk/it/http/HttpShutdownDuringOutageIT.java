package com.im.njams.sdk.it.http;

import java.util.Properties;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
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
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Scenario 4, once per discard mode: {@code stop()} must return promptly while the connection is down, whether the
 * mode holds messages ({@code none}: sender threads parked, submitters blocked on a full queue) or drops them.
 * No thread driving jobs may be left stuck after {@code stop()}.
 */
@RunWith(Parameterized.class)
public class HttpShutdownDuringOutageIT {

    @Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return Arrays.stream(DiscardMode.values()).map(m -> new Object[] { m }).collect(Collectors.toList());
    }


    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    private final DiscardMode mode;
    private Njams njams;

    public HttpShutdownDuringOutageIT(DiscardMode mode) {
        this.mode = mode;
    }

    /**
     * Safety net only — the test's own point is that {@code njams.stop()} inline, inside the
     * {@code @Test(timeout=...)} bound, completes promptly under outage; {@code @After} runs outside that bound,
     * so it must not become the primary way this instance is stopped. It only catches the case where the test
     * fails before reaching its own inline {@code stop()} call, so a failed run never leaves a live instance
     * reconnecting for the rest of the suite.
     */
    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 30000)
    public void stopCompletesPromptlyEvenWhileReconnecting() throws Exception {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "HTTP");
        settings.put(NjamsSettings.PROPERTY_HTTP_BASE_URL, env.httpBaseUrlThroughProxy());
        settings.put(NjamsSettings.PROPERTY_HTTP_DATAPROVIDER_SUFFIX, "dataprovider");
        mode.apply(settings);

        njams = new Njams(Path.of("HttpShutdownDuringOutageIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 100, 1);

        env.toxiproxy().addToxic("http", "shutdown-outage", "timeout", Map.of("timeout", 1));
        // More jobs than the default dispatch capacity (8 threads + 8 queue slots), so under 'none' the worker
        // threads park in acquire() and submitters block on the full queue -- the state stop() has to unwind.
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 30, 100, 4);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(300); // let the reconnect loop actually start

        // The @Test(timeout=...) above is the real assertion: stop() must not hang waiting on the reconnect loop.
        long start = System.nanoTime();
        njams.stop();
        long stopMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("stop() must return promptly during an outage, took " + stopMs + " ms", stopMs < 10_000);
        background.join(10_000);
        assertFalse("The job-driving thread must not stay blocked after stop() under " + mode, background.isAlive());
    }
}
