package com.im.njams.sdk.communication.lifecycle;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.AbstractSender;
import com.im.njams.sdk.communication.ConnectionStatus;

/**
 * Controllable fake sender for deterministic lifecycle tests. Connect behavior is driven by
 * {@link LifecycleTestTransport}.
 */
public class LifecycleTestSender extends AbstractSender {

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        setConnectionStatus(ConnectionStatus.CONNECTING);
        try {
            LifecycleTestTransport.onSenderConnect();
            setConnectionStatus(ConnectionStatus.CONNECTED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("interrupted during connect", e);
        } catch (RuntimeException e) {
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("connect failed", e);
        }
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        // no-op: lifecycle tests assert on connection state, not payload delivery
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        // no-op
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        // no-op
    }

    /** Test hook: forces DISCONNECTED so reconnect() can be exercised. */
    public void forceDisconnect() {
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }
}
