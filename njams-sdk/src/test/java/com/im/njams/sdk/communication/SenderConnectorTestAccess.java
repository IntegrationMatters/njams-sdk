package com.im.njams.sdk.communication;

import com.im.njams.sdk.communication.lifecycle.LifecycleTestTransport;
import com.im.njams.sdk.settings.ClientSettings;

/** Test bridge exposing the package-private SenderConnector to tests in other packages. */
public class SenderConnectorTestAccess {

    private final SenderConnector connector;
    private final ConnectionCoordinator coordinator;

    private SenderConnectorTestAccess(SenderConnector connector, ConnectionCoordinator coordinator) {
        this.connector = connector;
        this.coordinator = coordinator;
    }

    public static SenderConnectorTestAccess create() {
        ClientSettings settings = ClientSettings.from(LifecycleTestTransport.settings().getAllProperties());
        CommunicationFactory factory = new CommunicationFactory(settings);
        ConnectionCoordinator coordinator = new ConnectionCoordinator();
        SenderPool pool = new SenderPool(factory, coordinator);
        return new SenderConnectorTestAccess(new SenderConnector(factory, coordinator, pool, settings), coordinator);
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

    /** The group's connected state, replacing per-sender {@code isConnected()} assertions (see Appendix A.1). */
    public boolean isGroupConnected() {
        return coordinator.isGroupConnected();
    }

    /** Forces the group disconnected, standing in for the old {@code LifecycleTestSender.forceDisconnect()}. */
    public void forceGroupDisconnected() {
        coordinator.beginReconnect();
    }
}
