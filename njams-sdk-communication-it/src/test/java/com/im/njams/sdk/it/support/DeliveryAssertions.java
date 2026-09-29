package com.im.njams.sdk.it.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per-discard-mode outcome check shared by the outage scenarios (spec §6.1). */
public final class DeliveryAssertions {

    private DeliveryAssertions() {
    }

    /**
     * {@code none}: every driven job arrived and nothing was discarded. Other modes: nothing arrived that was not
     * driven, and every job that did not arrive is accounted for by a discard. Either way no {@code logId} arrived
     * more often than {@code maxDeliveriesPerLogId}.
     */
    public static void assertOutcome(DiscardMode mode, Collection<String> driven, Map<String, Integer> deliveries,
        int discarded, int maxDeliveriesPerLogId) {
        Set<String> drivenSet = new HashSet<>(driven);
        assertTrue("Delivered a logId that was never driven: " + deliveries.keySet(),
            drivenSet.containsAll(deliveries.keySet()));
        int max = deliveries.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue("No logId may arrive more than " + maxDeliveriesPerLogId + " times; observed " + deliveries,
            max <= maxDeliveriesPerLogId);
        int undelivered = drivenSet.size() - deliveries.size();
        if (mode.holdsMessages()) {
            assertEquals("Mode " + mode + " must never drop a job", 0, undelivered);
            assertEquals("Mode " + mode + " must never report a discard", 0, discarded);
        } else {
            assertTrue("Every undelivered job must be accounted for by a discard: undelivered=" + undelivered
                + ", discarded=" + discarded, undelivered <= discarded);
        }
    }
}
