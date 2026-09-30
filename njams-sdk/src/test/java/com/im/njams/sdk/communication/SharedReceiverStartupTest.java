package com.im.njams.sdk.communication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.settings.Settings;

/**
 * SDK-442: the startup outcome of a shared receiver must not leak into {@link Njams} instances that are created
 * after that outcome was decided.
 */
public class SharedReceiverStartupTest {

    private final List<Njams> instances = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        TestSharedReceiver.reset();
        clearCachedSharedReceivers();
    }

    @After
    public void tearDown() throws Exception {
        for (Njams njams : instances) {
            if (njams.isStarted()) {
                njams.stop();
            }
        }
        TestSharedReceiver.reset();
        clearCachedSharedReceivers();
    }

    @SuppressWarnings("unchecked")
    private static void clearCachedSharedReceivers() throws Exception {
        Field field = CommunicationFactory.class.getDeclaredField("sharedReceivers");
        field.setAccessible(true);
        Map<?, ?> sharedReceivers = (Map<?, ?>) field.get(null);
        synchronized (sharedReceivers) {
            sharedReceivers.clear();
        }
    }

    private Njams newNjams(String name) {
        Settings settings = TestSender.getSettings();
        settings.put(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, "true");
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, "200");
        Njams njams = new Njams(Path.of(name), "1.0", "test", settings);
        instances.add(njams);
        return njams;
    }

    private static TestSharedReceiver receiverOf(Njams njams) {
        return TestSharedReceiver.INSTANCES.stream().filter(r -> r.isUsedBy(njams)).findFirst().orElse(null);
    }

    @Test
    public void failedStartupDoesNotFailLaterInstance() {
        TestSharedReceiver.failConnect = true;
        assertFalse(newNjams("A").start());

        TestSharedReceiver.failConnect = false;
        Njams later = newNjams("B");
        assertTrue("start() must succeed once the backend is reachable", later.start());
        TestSharedReceiver receiver = receiverOf(later);
        assertNotNull(receiver);
        assertTrue(receiver.isConnected());
    }

    @Test
    public void timedOutStartupDoesNotFailLaterInstance() {
        TestSharedReceiver.connectDelayMs = 1000;
        assertFalse(newNjams("A").start());

        TestSharedReceiver.connectDelayMs = 0;
        Njams later = newNjams("B");
        assertTrue("start() must succeed once the backend is reachable", later.start());
        TestSharedReceiver receiver = receiverOf(later);
        assertNotNull(receiver);
        assertTrue(receiver.isConnected());
    }

    @Test
    public void runningInstancesShareOneReceiverThatSurvivesStopOfOneUser() {
        Njams first = newNjams("A");
        Njams second = newNjams("B");
        assertTrue(first.start());
        assertTrue(second.start());
        TestSharedReceiver shared = receiverOf(first);
        assertNotNull(shared);
        assertSame("running instances must share one receiver", shared, receiverOf(second));

        first.stop();
        assertTrue("stopping one user must not stop the receiver for the others", shared.isConnected());
        assertTrue(shared.isUsedBy(second));

        Njams third = newNjams("C");
        assertTrue(third.start());
        assertSame("a receiver that is still in use must be reused", shared, receiverOf(third));
    }

    /**
     * Creates the instances "A" and "B" on one shared receiver and lets the startup of "A" fail. "B" is not
     * started, so it still holds the failed receiver.
     */
    private Njams[] failStartupWhileReceiverIsHeldByAnotherInstance() {
        TestSharedReceiver.failConnect = true;
        // delay the failure, so that both instances get the receiver before its startup has failed
        TestSharedReceiver.connectDelayMs = 100;
        Njams failing = newNjams("A");
        Njams holder = newNjams("B");
        assertSame("precondition: both instances share one receiver", receiverOf(failing), receiverOf(holder));
        assertFalse(failing.start());
        TestSharedReceiver.connectDelayMs = 0;
        return new Njams[] { failing, holder };
    }

    @Test
    public void failedReceiverStillHeldByAnotherInstanceIsNotReused() {
        Njams[] instances = failStartupWhileReceiverIsHeldByAnotherInstance();
        Njams failing = instances[0];
        TestSharedReceiver failed = receiverOf(instances[1]);
        assertNotNull("the failed receiver must still be held by the instance that has not been started", failed);
        assertFalse("a failed start() must detach the instance from the shared receiver", failed.isUsedBy(failing));

        TestSharedReceiver.failConnect = false;
        Njams later = newNjams("C");
        assertTrue("start() must succeed once the backend is reachable", later.start());
        TestSharedReceiver replacement = receiverOf(later);
        assertNotNull(replacement);
        assertNotSame(failed, replacement);
        assertTrue(replacement.isConnected());
    }

    @Test
    public void releasingReplacedReceiverKeepsReplacementCached() {
        Njams holder = failStartupWhileReceiverIsHeldByAnotherInstance()[1];

        TestSharedReceiver.failConnect = false;
        Njams later = newNjams("C");
        assertTrue(later.start());
        TestSharedReceiver replacement = receiverOf(later);

        // the holder's start fails on the old receiver and releases it as its last user
        assertFalse(holder.start());
        assertTrue(replacement.isConnected());

        Njams next = newNjams("D");
        assertTrue(next.start());
        assertSame("releasing the old receiver must not evict its replacement", replacement, receiverOf(next));
    }

    @Test
    public void receiverStoppedByLastUserIsConnectedForLaterInstance() {
        Njams first = newNjams("A");
        assertTrue(first.start());
        first.stop();

        Njams later = newNjams("B");
        assertTrue(later.start());
        TestSharedReceiver receiver = receiverOf(later);
        assertNotNull(receiver);
        assertTrue("the receiver used by a newly started instance must be connected", receiver.isConnected());
    }
}
