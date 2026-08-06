package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Controllable fake receiver for deterministic lifecycle tests. Connect behavior is driven by
 * {@link LifecycleTestTransport}, mirroring {@link LifecycleTestSender}. Pairs with it under the same transport
 * name so a real Njams can start with both sides controllable.
 */
public class LifecycleTestReceiver extends AbstractReceiver {

    /** Mirrors {@link LifecycleTestSender#INSTANCES} — see its Javadoc for why teardown needs this registry. */
    private static final List<LifecycleTestReceiver> INSTANCES = new CopyOnWriteArrayList<>();

    public LifecycleTestReceiver() {
        INSTANCES.add(this);
    }

    /** Stops every registered receiver's daemon threads and clears the registry. Called from
     * {@link LifecycleTestTransport#shutdownAllReceivers()} in test teardown. */
    static void shutdownAll() {
        for (LifecycleTestReceiver r : INSTANCES) {
            r.setShouldShutdown(true);
            r.cancelReconnect();
        }
        INSTANCES.clear();
    }

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public void connect() {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            return;
        }
        connectionStatus = ConnectionStatus.CONNECTING;
        try {
            LifecycleTestTransport.onReceiverConnect();
            connectionStatus = ConnectionStatus.CONNECTED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("interrupted during connect", e);
        } catch (RuntimeException e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            throw new NjamsSdkRuntimeException("connect failed", e);
        }
    }

    @Override
    public void stop() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    /** Test hook: forces DISCONNECTED so reconnect() can be exercised. */
    public void forceDisconnect() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }
}
