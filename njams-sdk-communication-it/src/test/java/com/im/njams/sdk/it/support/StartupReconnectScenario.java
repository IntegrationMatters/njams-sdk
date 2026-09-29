package com.im.njams.sdk.it.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;
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
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.settings.Settings;

/**
 * Scenario 1b: startup with {@code startup.failbehavior=reconnect} while the target is unreachable, once per discard
 * mode. {@code start()} must succeed and the SDK must be initialized; from then on the sender behaves exactly as if
 * the connection problem had occurred later, i.e. according to the discard policy:
 * <ul>
 * <li>{@code none}: nothing is discarded; jobs driven while the target is down are held and delivered once the
 * background reconnect succeeds.</li>
 * <li>{@code onconnectionloss} / {@code discard}: everything produced while the group is reconnecting is dropped
 * (counted). That includes the startup project message, which is produced before the connection exists, as the FAQ
 * states for messages produced before the initial connection is established.</li>
 * <li>All modes: the reconnect runs on its own, ends once the target is back, and jobs driven afterwards are
 * delivered.</li>
 * </ul>
 * Concrete subclasses supply the transport.
 */
@RunWith(Parameterized.class)
public abstract class StartupReconnectScenario {

    private static final int JOBS_DURING_OUTAGE = 10;
    private static final int JOBS_AFTER_RECOVERY = 5;
    private static final String TOXIC = "startup-down";
    /** Short, so the startup connect gives up quickly and the background reconnect takes over. */
    private static final String CONNECT_TIMEOUT_MS = "2000";

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

    protected StartupReconnectScenario(DiscardMode mode) {
        this.mode = mode;
    }

    /** @return the Toxiproxy proxy name in front of this transport's server. */
    protected abstract String proxy();

    protected abstract void configureTransport(Settings settings);

    /** @return how often one logId may legitimately reach the server. */
    protected abstract int maxDeliveriesPerLogId();

    protected abstract Map<String, Integer> deliveries(Collection<String> logIds, DiscardMode mode,
        IntSupplier discards) throws Exception;

    /**
     * @return whether the startup project message reached the server within the timeout, or {@code null} if this
     *         transport cannot tell it apart from earlier runs' project messages.
     */
    protected Boolean projectMessageDelivered(Duration timeout) throws Exception {
        return null;
    }

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 120000)
    public void startSucceedsAndTheSenderBehavesAccordingToTheDiscardModeUntilItReconnects() throws Exception {
        env.toxiproxy().addToxic(proxy(), TOXIC, "timeout", Map.of("timeout", 1));

        Settings settings = new Settings();
        configureTransport(settings);
        mode.apply(settings);
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "RECONNECT");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);
        njams = new Njams(Path.of(getClass().getSimpleName()), "1.0.0", "CommunicationIT", settings);

        assertTrue("start() must succeed under the 'reconnect' startup policy although the target is down",
            njams.start());
        assertTrue("The SDK must be initialized although the target is down", njams.isStarted());
        ProcessModel model = FixedProcessModel.build(njams);

        Set<String> reconnectors = SdkThreads.awaitAny(Duration.ofSeconds(5), SdkThreads.SENDER_RECONNECTOR);
        assertFalse("The sender must be reconnecting in the background on its own", reconnectors.isEmpty());

        final int startupDiscards = awaitStartupDiscards();

        List<String> duringOutage = new CopyOnWriteArrayList<>();
        Thread driver = new Thread(() -> {
            try {
                duringOutage.addAll(MessageDriver.run(model, JOBS_DURING_OUTAGE, 100, 2));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        driver.start();
        if (mode.holdsMessages()) {
            Thread.sleep(DockerEnvironment.OUTAGE_MS);
            assertEquals("Mode " + mode + " must hold, not discard, while the target is down", 0, discards.count());
        } else {
            // Every job produced while the group is reconnecting must be dropped; the target stays down until
            // all of them have been, so this cannot race with the recovery.
            long deadline = System.nanoTime() + Duration.ofMillis(DockerEnvironment.OUTAGE_MS).toNanos();
            while (discards.count() < startupDiscards + JOBS_DURING_OUTAGE && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertTrue("Every job driven while the sender is reconnecting must be discarded under " + mode
                + ": discards " + discards.count() + ", expected at least "
                + (startupDiscards + JOBS_DURING_OUTAGE), discards.count() >= startupDiscards
                    + JOBS_DURING_OUTAGE);
        }

        env.toxiproxy().removeToxic(proxy(), TOXIC);
        driver.join(30_000);
        assertFalse("The driving thread must not stay blocked once the target is back", driver.isAlive());
        assertEquals(JOBS_DURING_OUTAGE, duringOutage.size());

        Set<String> stillReconnecting = SdkThreads.awaitNone(Duration.ofSeconds(20), SdkThreads.SENDER_RECONNECTOR);
        assertTrue("The background reconnect must end once the target is back; still alive: " + stillReconnecting,
            stillReconnecting.isEmpty());

        List<String> afterRecovery = MessageDriver.run(model, JOBS_AFTER_RECOVERY, 100, 1);
        List<String> driven = new ArrayList<>(duringOutage);
        driven.addAll(afterRecovery);
        // Counted separately from the startup project message's discard, which no driven job accounts for. Drained
        // once: a JMS drain consumes what it reads.
        IntSupplier jobDiscards = () -> discards.count() - startupDiscards;
        Map<String, Integer> deliveries = deliveries(driven, mode, jobDiscards);
        assertTrue("Jobs driven after the reconnect must be delivered under " + mode + "; delivered "
            + deliveries.keySet() + " of " + afterRecovery, deliveries.keySet().containsAll(afterRecovery));
        DeliveryAssertions.assertOutcome(mode, driven, deliveries, jobDiscards.getAsInt(), maxDeliveriesPerLogId());
        if (!mode.holdsMessages()) {
            assertTrue("Jobs driven while the sender was reconnecting must not be delivered later under " + mode
                + ": " + deliveries.keySet(), Collections.disjoint(deliveries.keySet(), duringOutage));
        }

        Boolean projectMessage = projectMessageDelivered(Duration.ofSeconds(mode.holdsMessages() ? 15 : 3));
        if (mode.holdsMessages() && projectMessage != null) {
            assertTrue("Under 'none' the startup project message is held and delivered after the reconnect",
                projectMessage);
        }
        // Observed only, not asserted, for the dropping modes: whether a project message dropped during startup is
        // ever re-sent is not documented behavior.
        System.out.println("[startup-reconnect] mode=" + mode + " projectMessageDelivered=" + projectMessage);
    }

    /**
     * Under the dropping modes the startup project message, produced before any connection exists, is the first
     * message to be discarded; waits for it and returns the count reached. Under {@code none} it must not be
     * discarded, so the count stays 0.
     */
    private int awaitStartupDiscards() throws InterruptedException {
        if (mode.holdsMessages()) {
            return discards.count();
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (discards.count() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue("The startup project message is produced while no connection exists and must be discarded under "
            + mode, discards.count() >= 1);
        return discards.count();
    }
}
