package com.im.njams.sdk.it.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import com.im.njams.sdk.it.support.Deliveries;
import com.im.njams.sdk.it.support.DeliveryAssertions;
import com.im.njams.sdk.it.support.DiscardMode;
import com.im.njams.sdk.it.support.DiscardObserver;
import com.im.njams.sdk.it.support.DockerEnvironment;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * Scenario 2, once per discard mode: {@code none} holds every job across the outage and delivers it after
 * recovery; {@code onconnectionloss} and {@code discard} drop the jobs sent while the connection is lost and
 * account for each of them as a discard.
 */
@RunWith(Parameterized.class)
public class MidProcessingOutageRecoveryIT {

    /** One attempt plus at most one ambiguous-outcome retry. */
    private static final int MAX_DELIVERIES_PER_LOG_ID = 2;

    @Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return Arrays.stream(DiscardMode.values()).map(m -> new Object[] { m }).collect(java.util.stream.Collectors.toList());
    }

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Rule
    public DiscardObserver discards = new DiscardObserver();

    private final DiscardMode mode;
    private Njams njams;

    public MidProcessingOutageRecoveryIT(DiscardMode mode) {
        this.mode = mode;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test
    public void jobsAreHeldOrDiscardedAcrossAnOutageAccordingToTheDiscardMode() throws Exception {
        Settings settings = new Settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION, "JMS");
        settings.put(NjamsSettings.PROPERTY_JMS_PROVIDER_URL, env.jmsUrlThroughProxy());
        env.configureJms(settings);
        mode.apply(settings);

        njams = new Njams(Path.of("MidProcessingOutageRecoveryIT"), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);

        List<String> beforeOutage = MessageDriver.run(model, 20, 100, 4);

        // The outage is armed before the second batch starts, so every job of that batch is driven into it.
        env.toxiproxy().addToxic("jms", "mid-outage", "timeout", Map.of("timeout", 1));
        List<String> duringOutage = new ArrayList<>();
        Thread outageDriver = new Thread(() -> {
            try {
                duringOutage.addAll(MessageDriver.run(model, 20, 100, 4));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        outageDriver.start();
        Thread.sleep(DockerEnvironment.OUTAGE_MS);
        env.toxiproxy().removeToxic("jms", "mid-outage");
        outageDriver.join(TimeUnit.SECONDS.toMillis(30));

        List<String> driven = new ArrayList<>(beforeOutage);
        driven.addAll(duringOutage);
        assertEquals(40, driven.size());
        assertEquals(40, Set.copyOf(driven).size());

        Map<String, Integer> deliveries = Deliveries.viaJms(env, driven, mode, discards::count);
        DeliveryAssertions.assertOutcome(mode, driven, deliveries, discards.count(), MAX_DELIVERIES_PER_LOG_ID);

        if (!mode.holdsMessages()) {
            assertTrue("Jobs driven into the outage must be discarded, but none was", discards.count() >= 1);
            assertFalse("Not every job driven into the outage may survive under " + mode,
                deliveries.keySet().containsAll(duringOutage));
        }
    }
}
