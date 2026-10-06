package com.im.njams.sdk.it.jms;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
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
import com.im.njams.sdk.it.support.SdkThreads;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Scenario 5, once per discard mode: repeated flapping must not accumulate threads in any mode; jobs driven into
 * the flaps are held under {@code none} (nothing discarded) and dropped, and counted, under the other modes.
 */
@RunWith(Parameterized.class)
public class RepeatedFlapIT {

    private static final int MAX_SENDER_THREADS = 8;

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

    public RepeatedFlapIT(DiscardMode mode) {
        this.mode = mode;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 120000)
    public void repeatedFlappingDoesNotAccumulateThreads() throws Exception {
        ClientSettings settings = ClientSettings.from(new Properties());
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);
        mode.apply(settings);
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, String.valueOf(MAX_SENDER_THREADS));

        njams = new Njams(Path.of("RepeatedFlapIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        MessageDriver.run(model, 5, 50, 1);

        // Two warm-up flaps first: they bring the sender pool to the steady state a flapping connection produces
        // (under 'none' the pool grows up to its configured maximum while callers are held), so the baseline
        // below measures accumulation across cycles, not that one-time, bounded growth.
        for (int i = 0; i < 2; i++) {
            flapOnce(model);
        }
        Thread.sleep(1000);
        Map<String, Integer> baselineGroups = threadGroups();
        int baselineThreadCount = total(baselineGroups);

        for (int i = 0; i < 10; i++) {
            flapOnce(model);
        }

        // Allow reconnect threads from the last cycle a moment to actually terminate.
        Thread.sleep(1000);
        // The receiver's recovery cycle (started once per sender-group recovery) may still be closing a dead
        // connection; it is transient, so wait for it before counting. A thread that never ends still fails below.
        SdkThreads.awaitNone(Duration.ofSeconds(30), "Receiver-Recovery-Cycle-Thread");

        // Modes that drop never build a backlog, so nothing may grow. Mode 'none' holds the flapped jobs, and that
        // backlog can legitimately grow the sender pool up to its configured maximum: each extra sender is one
        // worker thread (its ActiveMQ transport threads are third-party and not counted). That growth is bounded by
        // the pool size; anything beyond it is a leak.
        int allowedGrowth = 2 + (mode.holdsMessages() ? MAX_SENDER_THREADS - 1 : 0);
        Map<String, Integer> finalGroups = threadGroups();
        int finalThreadCount = total(finalGroups);
        assertTrue("Thread count grew from " + baselineThreadCount + " to " + finalThreadCount
            + " across 10 flap cycles (allowed +" + allowedGrowth + ") -- suspect a thread leak. Groups (name with "
            + "digits masked) that grew: " + growth(baselineGroups, finalGroups),
            finalThreadCount <= baselineThreadCount + allowedGrowth);
        long reconnectors = finalGroups.entrySet().stream().filter(e -> e.getKey().contains("Reconnector"))
            .mapToInt(Map.Entry::getValue).sum();
        assertTrue("At most one reconnector per connection group (sender, receiver) may be alive; found "
            + reconnectors + " in " + finalGroups, reconnectors <= 2);

        if (mode.holdsMessages()) {
            assertEquals("Mode none must never discard", 0, discards.count());
        } else {
            assertTrue("Jobs driven into the flaps must be discarded under " + mode, discards.count() >= 1);
        }
    }

    private void flapOnce(ProcessModel model) throws Exception {
        env.toxiproxy().addToxic("jms", "flap", "timeout", Map.of("timeout", 1));
        Thread background = new Thread(() -> {
            try {
                MessageDriver.run(model, 3, 50, 2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        background.start();
        Thread.sleep(200);
        env.toxiproxy().removeToxic("jms", "flap");
        background.join(10000);
    }

    /** Threads owned by third-party libraries; their pooling is not under the SDK's control and not asserted. */
    private static boolean isForeign(String threadName) {
        return threadName.startsWith("OkHttp") || threadName.startsWith("ActiveMQ");
    }

    private static int total(Map<String, Integer> groups) {
        return groups.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static Map<String, Integer> threadGroups() {
        Map<String, Integer> groups = new TreeMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (isForeign(thread.getName())) {
                continue;
            }
            groups.merge(thread.getName().replaceAll("\\d+", "#"), 1, Integer::sum);
        }
        return groups;
    }

    private static Map<String, Integer> growth(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> grown = new TreeMap<>();
        after.forEach((name, count) -> {
            int delta = count - before.getOrDefault(name, 0);
            if (delta > 0) {
                grown.put(name, delta);
            }
        });
        return grown;
    }
}
