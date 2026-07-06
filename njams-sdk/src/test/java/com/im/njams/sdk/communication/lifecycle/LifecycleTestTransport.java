package com.im.njams.sdk.communication.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.settings.Settings;

/** Test-only controls shared by {@link LifecycleTestSender} and {@link LifecycleTestReceiver}. */
public final class LifecycleTestTransport {

    public static final String NAME = "LIFECYCLE_TEST";

    /** How the next sender connect attempt behaves. */
    public enum ConnectMode { SUCCEED, FAIL, BLOCK }

    private static volatile ConnectMode senderMode = ConnectMode.SUCCEED;
    /** Released to let a BLOCK-ing connect proceed (to SUCCEED). Recreated by {@link #reset()}. */
    private static volatile CountDownLatch blockRelease = new CountDownLatch(1);
    /** Counts down once per sender connect entry (so tests can await that a connect was attempted). */
    private static volatile CountDownLatch connectAttempted = new CountDownLatch(1);
    private static final AtomicInteger senderConnectCount = new AtomicInteger(0);

    private LifecycleTestTransport() {
    }

    /** Resets all controls to the default (SUCCEED) state. Call in @Before. */
    public static void reset() {
        senderMode = ConnectMode.SUCCEED;
        blockRelease = new CountDownLatch(1);
        connectAttempted = new CountDownLatch(1);
        senderConnectCount.set(0);
    }

    public static void setSenderMode(ConnectMode mode) {
        senderMode = mode;
    }

    /** Lets a BLOCK-ing sender connect finish successfully. */
    public static void releaseBlockedConnect() {
        blockRelease.countDown();
    }

    /** @return a fresh latch that fires the next time a sender connect is attempted. */
    public static CountDownLatch connectAttemptedLatch() {
        return connectAttempted;
    }

    public static int senderConnectCount() {
        return senderConnectCount.get();
    }

    // called by LifecycleTestSender.connect()
    static void onSenderConnect() throws InterruptedException {
        senderConnectCount.incrementAndGet();
        connectAttempted.countDown();
        connectAttempted = new CountDownLatch(1);
        switch (senderMode) {
        case FAIL:
            throw new IllegalStateException("LIFECYCLE_TEST: connect configured to FAIL");
        case BLOCK:
            blockRelease.await();
            return;
        case SUCCEED:
        default:
            return;
        }
    }

    /** Settings selecting this transport with the in-memory configuration provider. */
    public static Settings settings() {
        Settings s = new Settings();
        s.put(NjamsSettings.PROPERTY_COMMUNICATION, NAME);
        s.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, "memory");
        return s;
    }
}
