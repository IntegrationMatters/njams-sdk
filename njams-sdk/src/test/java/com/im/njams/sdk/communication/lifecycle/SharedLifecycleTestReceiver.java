package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.communication.SharedReceiverSupport;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Shareable counterpart of {@link LifecycleTestReceiver}, mirroring the production
 * {@code JmsReceiver}/{@code SharedJmsReceiver} pairing: same transport name ({@link LifecycleTestTransport#NAME}),
 * connect/stop behavior inherited unchanged, with {@link ShareableReceiver} support layered on top via
 * {@link SharedReceiverSupport}. {@code CommunicationFactory.findReceiverType} selects this class instead of the
 * plain {@link LifecycleTestReceiver} whenever {@code njams.sdk.communication.shared=true}, exactly as it selects
 * {@code SharedJmsReceiver} over {@code JmsReceiver}.
 */
public class SharedLifecycleTestReceiver extends LifecycleTestReceiver implements ShareableReceiver<Object> {

    /**
     * Tracks every instance actually wired into service, so tests can assert eviction produced a genuinely fresh
     * instance. Registration happens in {@link #init(ClientSettings)} rather than the constructor: {@code
     * CommunicationFactory}'s SPI lookup (see {@code findReceiverType}) instantiates-and-discards throwaway
     * instances of every registered {@code Receiver} class just to read their {@code getName()} — including this
     * one, possibly several times per {@code getReceiver(Njams)} call, e.g. once per sharing {@code Njams}
     * instance even though only the first construction is actually wired up. Only the instance that is really put
     * into service ever has {@link #init(ClientSettings)} called on it (a reused, cached shared instance does
     * not get {@code init} called again either), so this is the only reliable "really used" signal.
     */
    private static final List<SharedLifecycleTestReceiver> INSTANCES = new CopyOnWriteArrayList<>();

    private final SharedReceiverSupport<SharedLifecycleTestReceiver, Object> sharingSupport =
        new SharedReceiverSupport<>(this);

    @Override
    public void init(ClientSettings settings) {
        super.init(settings);
        INSTANCES.add(this);
    }

    /**
     * Test-only accessor mirroring {@link LifecycleTestReceiver#lastCreated()}.
     *
     * @return the most recently initialized instance, or {@code null} if none is registered.
     */
    static SharedLifecycleTestReceiver lastCreated() {
        return INSTANCES.isEmpty() ? null : INSTANCES.get(INSTANCES.size() - 1);
    }

    /**
     * Test-only teardown: clears this class's own tracking registry. Does <b>not</b> touch
     * {@code CommunicationFactory}'s shared-receiver cache — that is expected to already be empty for this class
     * by the time a well-behaved test calls this, because every shared receiver it created was evicted by
     * {@code Njams.stop()} once its last user was removed.
     */
    static void clearInstanceRegistry() {
        INSTANCES.clear();
    }

    @Override
    public void setNjams(Njams njamsInstance) {
        sharingSupport.addNjams(njamsInstance);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cancels any in-progress reconnect once really stopped, mirroring {@code SharedJmsReceiver#removeNjams}.
     */
    @Override
    public boolean removeNjams(Njams njamsInstance) {
        boolean reallyStopped = sharingSupport.removeNjams(njamsInstance);
        if (reallyStopped) {
            cancelReconnect();
        }
        return reallyStopped;
    }

    @Override
    public Path getReceiverPath(Object requestMessage, Instruction instruction) {
        return null;
    }

    @Override
    public String getClientId(Object requestMessage, Instruction instruction) {
        return null;
    }

    @Override
    public void sendReply(Object requestMessage, Instruction reply, String clientId) {
        // not exercised by these tests
    }
}
