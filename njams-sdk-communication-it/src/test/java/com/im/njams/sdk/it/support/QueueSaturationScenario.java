package com.im.njams.sdk.it.support;

import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

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
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Scenario 10: saturation of the sender's dispatch queue, once per discard mode and per kind of blockage. The
 * dispatch pool is shrunk to one sender thread and a two-slot queue, so a handful of jobs saturates it.
 * <ul>
 * <li>{@link Blockage#SLOW}: the connection stays up but every exchange is slow. {@code none} and
 * {@code onconnectionloss} apply back-pressure (the submitting thread blocks, nothing is dropped); {@code discard}
 * drops what does not fit the queue.</li>
 * <li>{@link Blockage#DOWN}: the connection is lost. {@code none} blocks the submitter until the connection is
 * back and then delivers everything; {@code onconnectionloss} and {@code discard} drop.</li>
 * </ul>
 * Concrete subclasses supply the transport.
 */
@RunWith(Parameterized.class)
public abstract class QueueSaturationScenario {

    /** How the transport is blocked while jobs are driven. */
    public enum Blockage {
        SLOW, DOWN
    }

    private static final int JOBS = 8;
    private static final String TOXIC = "saturation";
    /** Per-direction delay; a single exchange therefore takes well over a second. */
    private static final int LATENCY_MS = 700;

    @Parameters(name = "{0}-{1}")
    public static Collection<Object[]> combinations() {
        List<Object[]> combinations = new ArrayList<>();
        for (DiscardMode mode : DiscardMode.values()) {
            for (Blockage blockage : Blockage.values()) {
                combinations.add(new Object[] { mode, blockage });
            }
        }
        return combinations;
    }

    @Rule
    public DockerEnvironment env = new DockerEnvironment();

    @Rule
    public DiscardObserver discards = new DiscardObserver();

    private final DiscardMode mode;
    private final Blockage blockage;
    private Njams njams;

    protected QueueSaturationScenario(DiscardMode mode, Blockage blockage) {
        this.mode = mode;
        this.blockage = blockage;
    }

    /** @return the Toxiproxy proxy name in front of this transport's server. */
    protected abstract String proxy();

    protected abstract void configureTransport(ClientSettings settings);

    /** @return how often one logId may legitimately reach the server (attempts plus resend after reconnect). */
    protected abstract int maxDeliveriesPerLogId();

    protected abstract Map<String, Integer> deliveries(Collection<String> logIds, DiscardMode mode,
        IntSupplier discards) throws Exception;

    @After
    public void tearDown() {
        if (njams != null && njams.isStarted()) {
            njams.stop();
        }
    }

    @Test(timeout = 120000)
    public void aSaturatedDispatchQueueBlocksOrDropsAccordingToTheDiscardMode() throws Exception {
        ClientSettings settings = ClientSettings.from(new Properties());
        configureTransport(settings);
        mode.apply(settings);
        settings.put(NjamsSettings.PROPERTY_MIN_SENDER_THREADS, "1");
        settings.put(NjamsSettings.PROPERTY_MAX_SENDER_THREADS, "1");
        settings.put(NjamsSettings.PROPERTY_MAX_QUEUE_LENGTH, "2");

        njams = new Njams(Path.of(getClass().getSimpleName()), "1.0.0", "CommunicationIT", settings);
        njams.start();
        ProcessModel model = FixedProcessModel.build(njams);
        // Connected and past the startup traffic before the blockage starts, so only the driven jobs are affected.
        List<String> warmUp = MessageDriver.run(model, 1, 100, 1);
        assertEquals(1, deliveries(warmUp, DiscardMode.NONE, () -> 0).size());
        assertEquals(0, discards.count());

        if (blockage == Blockage.SLOW) {
            env.toxiproxy().addToxic(proxy(), TOXIC, "latency", Map.of("latency", LATENCY_MS));
        } else {
            env.toxiproxy().addToxic(proxy(), TOXIC, "timeout", Map.of("timeout", 1));
        }
        List<String> driven = new CopyOnWriteArrayList<>();
        Thread driver = new Thread(() -> {
            try {
                driven.addAll(MessageDriver.run(model, JOBS, 100, 1));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        driver.start();

        boolean backPressure = mode.holdsMessages()
            || (mode == DiscardMode.ON_CONNECTION_LOSS && blockage == Blockage.SLOW);
        if (backPressure) {
            Thread.sleep(1500);
            assertTrue("The submitting thread must be blocked by the saturated queue under " + mode + "/"
                + blockage, driver.isAlive());
            if (blockage == Blockage.DOWN) {
                Thread.sleep(DockerEnvironment.OUTAGE_MS);
                env.toxiproxy().removeToxic(proxy(), TOXIC);
            }
            driver.join(60_000);
            assertFalse("The submitting thread must be released once the queue drains", driver.isAlive());
        } else {
            driver.join(10_000);
            assertFalse("Under " + mode + "/" + blockage + " the submitting thread must never be blocked",
                driver.isAlive());
        }
        assertEquals(JOBS, driven.size());

        Map<String, Integer> deliveries = deliveries(driven, mode, discards::count);
        DeliveryAssertions.assertOutcome(mode, driven, deliveries, discards.count(), maxDeliveriesPerLogId());
        if (!backPressure) {
            assertTrue("Saturation/loss must have caused discards under " + mode + "/" + blockage,
                discards.count() >= 1);
        }
    }
}
