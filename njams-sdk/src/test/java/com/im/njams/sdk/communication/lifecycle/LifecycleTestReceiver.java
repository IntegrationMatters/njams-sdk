package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractReceiver;
import com.im.njams.sdk.communication.ConnectionStatus;
import com.im.njams.sdk.settings.ClientSettings;

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

    /**
     * {@inheritDoc}
     * <p>
     * Honors {@link LifecycleTestTransport#armReceiverConstructionFailOnce()}. The check lives here rather than
     * in the constructor because {@code CommunicationFactory}'s SPI lookup constructs-and-discards throwaway
     * probe instances of every registered {@code Receiver} class just to read {@code getName()}/check {@code
     * instanceof} (see {@link SharedLifecycleTestReceiver}'s Javadoc for the full explanation) — a constructor
     * hook would be consumed by one of those probes instead of the real, selected instance. {@code init(...)} is
     * called only once, on the instance {@code CommunicationFactory.createReceiver} actually puts into service.
     */
    @Override
    public void init(ClientSettings settings) {
        if (LifecycleTestTransport.consumeReceiverConstructionFailure()) {
            throw new NjamsSdkRuntimeException("LIFECYCLE_TEST: receiver construction configured to fail");
        }
        super.init(settings);
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

    /**
     * Test-only accessor for specs that need to reach the concrete receiver a real {@link com.im.njams.sdk.Njams}
     * instance wired internally (there is no public getter on {@code Njams} for its receiver, by design — the
     * communication layer is not public API). Returns the most recently constructed instance still registered.
     *
     * @return the most recently constructed instance, or {@code null} if none is registered.
     */
    static LifecycleTestReceiver lastCreated() {
        return INSTANCES.isEmpty() ? null : INSTANCES.get(INSTANCES.size() - 1);
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
