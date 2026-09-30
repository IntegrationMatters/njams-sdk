package com.im.njams.sdk.communication;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Shareable test receiver, selected instead of {@link TestReceiver} when
 * {@link com.im.njams.sdk.NjamsSettings#PROPERTY_SHARED_COMMUNICATIONS} is enabled.
 * The connect behavior is controlled via the static fields.
 */
public class TestSharedReceiver extends AbstractReceiver implements ShareableReceiver<Object> {

    static volatile boolean failConnect = false;
    static volatile long connectDelayMs = 0;
    static final List<TestSharedReceiver> INSTANCES = new CopyOnWriteArrayList<>();

    private final SharedReceiverSupport<TestSharedReceiver, Object> sharingSupport =
        new SharedReceiverSupport<>(this);

    public TestSharedReceiver() {
        INSTANCES.add(this);
    }

    static void reset() {
        failConnect = false;
        connectDelayMs = 0;
        INSTANCES.clear();
    }

    boolean isUsedBy(Njams njams) {
        return sharingSupport.getAllNjamsInstances().contains(njams);
    }

    @Override
    public String getName() {
        return TestReceiver.NAME;
    }

    @Override
    public void connect() {
        if (connectDelayMs > 0) {
            try {
                Thread.sleep(connectDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failConnect) {
            throw new NjamsSdkRuntimeException("Simulated connect failure");
        }
        connectionStatus = ConnectionStatus.CONNECTED;
    }

    @Override
    public void stop() {
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    @Override
    public void setNjams(Njams njams) {
        super.setNjams(null);
        sharingSupport.addNjams(njams);
    }

    @Override
    public void removeNjams(Njams njams) {
        sharingSupport.removeNjams(njams);
    }

    @Override
    public void onInstruction(Instruction instruction) {
        throw new UnsupportedOperationException();
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
        // nothing
    }
}
