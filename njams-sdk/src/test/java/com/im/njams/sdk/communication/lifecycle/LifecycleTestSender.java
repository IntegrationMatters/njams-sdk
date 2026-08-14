package com.im.njams.sdk.communication.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
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

    /**
     * Every instance ever created (also the pooled ones spun up by a real {@code NjamsSender}). Lets test
     * teardown stop any daemon reconnect/startup thread a test spawned, so it cannot survive into a later test
     * and count down that test's shared latches.
     */
    private static final List<LifecycleTestSender> INSTANCES = new CopyOnWriteArrayList<>();

    private volatile boolean closed = false;

    public LifecycleTestSender() {
        INSTANCES.add(this);
    }

    /**
     * Stops every registered sender's daemon threads (reconnect loop and any blocking startup connect) and clears
     * the registry. Called from {@link LifecycleTestTransport#shutdownAllSenders()} in test teardown.
     */
    static void shutdownAll() {
        for (LifecycleTestSender s : INSTANCES) {
            s.setShouldShutdown(true);
            s.cancelReconnect();
        }
        INSTANCES.clear();
    }

    @Override
    public String getName() {
        return LifecycleTestTransport.NAME;
    }

    @Override
    public void close() {
        closed = true;
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }

    /** @return {@code true} once {@link #close()} has been called on this instance. */
    public boolean wasClosed() {
        return closed;
    }

    /**
     * Test hook standing in for a transport that discovers a broken connection asynchronously (outside a
     * {@code send(...)} call) and reports it to its owning pool, mirroring {@code JmsSender.onException}.
     *
     * @param cause the failure to report.
     */
    public void reportAsyncFailure(Exception cause) {
        notifyConnectionFailure(cause);
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

    /**
     * Dispatches to the typed {@code send} methods and lets any failure propagate, which is the SPI contract
     * {@code SenderPool}/{@code NjamsSender} rely on: one honest attempt per sender, the caller retires the sender
     * and retries the message on a fresh one.
     * <p>
     * Overridden deliberately rather than inheriting {@link AbstractSender#send(CommonMessage, String)}: the base
     * class still carries the legacy per-sender retry/discard loop, which swallows every send failure internally
     * and therefore hides the pool's retention loop from any test driving it. That loop is reduced to plain
     * {@code instanceof} dispatch in the following task, at which point this override becomes redundant with its
     * superclass rather than a departure from it.
     *
     * @param msg             the message to send.
     * @param clientSessionId the sending client session ID.
     */
    @Override
    public void send(CommonMessage msg, String clientSessionId) {
        if (msg instanceof LogMessage) {
            send((LogMessage) msg, clientSessionId);
        } else if (msg instanceof ProjectMessage) {
            send((ProjectMessage) msg, clientSessionId);
        } else if (msg instanceof TraceMessage) {
            send((TraceMessage) msg, clientSessionId);
        }
    }

    @Override
    protected void send(LogMessage msg, String clientSessionId) {
        failIfArmed();
        LifecycleTestTransport.onSuccessfulSend();
    }

    @Override
    protected void send(ProjectMessage msg, String clientSessionId) {
        failIfArmed();
        LifecycleTestTransport.onSuccessfulSend();
    }

    @Override
    protected void send(TraceMessage msg, String clientSessionId) {
        failIfArmed();
        LifecycleTestTransport.onSuccessfulSend();
    }

    /**
     * If the block-then-fail send mode is armed, blocks until released and then fails the send the way a real
     * transport does on a broken connection: drop to DISCONNECTED and throw. Otherwise a no-op (default), so the
     * caller goes on to count the send as successful.
     */
    private void failIfArmed() {
        try {
            if (LifecycleTestTransport.awaitSendGateIfArmed()) {
                setConnectionStatus(ConnectionStatus.DISCONNECTED);
                throw new NjamsSdkRuntimeException("LIFECYCLE_TEST: send configured to fail during drain");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("interrupted during send", e);
        }
    }

    /** Test hook: forces DISCONNECTED so reconnect() can be exercised. */
    public void forceDisconnect() {
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }
}
