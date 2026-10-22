package com.im.njams.sdk.communication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;

/** Test bridge exposing the package-private SenderConnector to tests in other packages. */
public class SenderConnectorTestAccess {

    /**
     * Every connector a test created. Teardown must stop these: a connector owns the group's startup/reconnect
     * daemon threads, and a reconnect loop left retrying against a FAIL-mode transport would otherwise survive
     * into the <em>next</em> test and count down that test's shared connect-attempt latch.
     */
    private static final List<SenderConnectorTestAccess> INSTANCES = new CopyOnWriteArrayList<>();

    private final SenderConnector connector;
    private final ConnectionCoordinator coordinator;

    private SenderConnectorTestAccess(SenderConnector connector, ConnectionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;
    }

    /**
     * Flags every connector's group as shutting down and interrupts its threads, then clears the registry. Called
     * from the shared lifecycle teardown.
     */
    public static void shutdownAll() {
        for (SenderConnectorTestAccess access : INSTANCES) {
            access.coordinator.setShouldShutdown(true);
            access.connector.cancelReconnect();
        }
        INSTANCES.clear();
    }

    public static SenderConnectorTestAccess create() {
        return create(false);
    }

    /**
     * Creates a connector whose coordinator has the Phase-1 "reconnect" startup policy armed up front (see
     * {@link ConnectionCoordinator#allowReconnectBeforeConnected()}), so a failed startup connect is permitted to
     * hand off into the background reconnect loop instead of just failing fast.
     */
    public static SenderConnectorTestAccess createWithReconnectBeforeConnected() {
        return create(true);
    }

    /**
     * Creates a connector whose <em>settings</em> select the {@code reconnect} startup policy, without arming
     * {@link ConnectionCoordinator#allowReconnectBeforeConnected()} up front. This is what production looks like
     * while the constructor's pre-warm connect runs: the policy is known from the settings, but the flag is only
     * set later, by {@code NjamsSender.startWithTimeout}.
     */
    public static SenderConnectorTestAccess createWithReconnectPolicy() {
        ClientSettings settings = LifecycleTestTransport.settings();
        settings.put(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, "reconnect");
        return create(settings, false);
    }

    private static SenderConnectorTestAccess create(boolean allowReconnectBeforeConnected) {
        return create(LifecycleTestTransport.settings(), allowReconnectBeforeConnected);
    }

    private static SenderConnectorTestAccess create(ClientSettings settings, boolean allowReconnectBeforeConnected) {
        CommunicationFactory factory = new CommunicationFactory(settings);
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        if (allowReconnectBeforeConnected) {
            coordinator.allowReconnectBeforeConnected();
        }
        SenderPool pool = new SenderPool(factory, coordinator);
        SenderConnectorTestAccess access =
            new SenderConnectorTestAccess(new SenderConnector(factory, coordinator, pool, settings), coordinator);
        INSTANCES.add(access);
        return access;
    }

    public void beginConnect() {
        connector.beginConnect();
    }

    public boolean awaitStartup(long timeoutMs) {
        return connector.awaitStartup(timeoutMs);
    }

    public void startReconnect(Exception cause) {
        connector.startReconnect(cause);
    }

    public void cancelReconnect() {
        connector.cancelReconnect();
    }

    /** Cancels the startup connect only, standing in for the fail-fast/timeout path's {@code cancelConnect()}. */
    public void cancelStartupConnect() {
        connector.cancelStartupConnect();
    }

    /** The group's connected state, replacing per-sender {@code isConnected()} assertions (see Appendix A.1). */
    public boolean isGroupConnected() {
        return coordinator.isGroupConnected();
    }

    /** Sets the group's shutdown flag, standing in for the old {@code AbstractSender.setShouldShutdown(...)}. */
    public void setShouldShutdown(boolean shutdown) {
        coordinator.setShouldShutdown(shutdown);
    }

    /** Forces the group disconnected, standing in for the old {@code LifecycleTestSender.forceDisconnect()}. */
    public void forceGroupDisconnected() {
        coordinator.beginReconnect();
    }
}
