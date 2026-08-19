/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Superclass for all Senders. Extend this class to create a nJAMS sender implementation that can send
 * project- and log-messages to the nJAMS server. When writing your own Sender, extend this class and
 * override methods when needed. All Senders are automatically pooled by the SDK; you must not implement
 * your own connection pooling!
 * <p>
 * <b>A {@code send} implementation must not block indefinitely.</b> The built-in transports each bound their
 * own retries and throw once exhausted; do the same in your own implementation.
 * <p>
 * The SDK also drives the connection lifecycle: implement {@link #connect()}, {@link #close()} and the typed
 * {@code send} methods as single honest attempts that throw on failure, and do not implement reconnect logic —
 * the SDK runs exactly one reconnect per sender group. If your transport detects a broken connection
 * asynchronously, report it with {@link #notifyConnectionFailure(Exception)}.
 * <p>
 * Senders are internal SDK infrastructure, not part of the user-facing API — client applications must not use
 * this class or obtain a sender instance directly.
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public abstract class AbstractSender {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSender.class);

    private ConnectionStatus connectionStatus;
    protected DiscardPolicy discardPolicy = DiscardPolicy.DEFAULT;
    protected ClientSettings settings;

    private volatile SenderFailureSink failureSink;

    /**
     * returns a new AbstractSender
     */
    public AbstractSender() {
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }

    /**
     * Initializes this sender via the given settings.
     *
     * @param settings the settings to be used for initialization
     */
    public void init(ClientSettings settings) {
        this.settings = settings;
        discardPolicy = DiscardPolicy.byValue(settings.getProperty(NjamsSettings.PROPERTY_DISCARD_POLICY));
    }

    /**
     * Each implementation must provide a unique name identifying this sender.
     *
     * @return this implementation's name
     */
    public abstract String getName();

    /**
     * override this method to implement your own connection initialization
     *
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    public synchronized void connect() throws NjamsSdkRuntimeException {
        if (isConnected()) {
            return;
        }
        try {
            setConnectionStatus(ConnectionStatus.CONNECTING);
            LOG.debug("Connecting...");
            setConnectionStatus(ConnectionStatus.CONNECTED);
        } catch (Exception e) {
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
            throw new NjamsSdkRuntimeException("Unable to connect", e);
        }

    }

    protected synchronized void setConnectionStatus(ConnectionStatus newConnectionStatus) {
        this.connectionStatus = newConnectionStatus;
    }

    protected synchronized ConnectionStatus getConnectionStatus() {
        return this.connectionStatus;
    }

    /**
     * Dispatches the given message to the matching typed {@code send} method. One honest attempt: any failure
     * propagates to the caller, which retires this sender and retries the message on a fresh one.
     * <p>
     * The connection is guaranteed to be established — {@link SenderPool#acquire()} only hands out connected
     * senders — so this method does not check the connection status, retry, or apply the discard policy. Those
     * are the pool's responsibility.
     *
     * @param msg             the message to send
     * @param clientSessionId the session ID of the {@link com.im.njams.sdk.Njams} instance that sends the message
     */
    public void send(CommonMessage msg, String clientSessionId) {
        LOG.trace("Sending message {}, state={}", msg, getConnectionStatus());
        if (msg instanceof LogMessage) {
            send((LogMessage) msg, clientSessionId);
        } else if (msg instanceof ProjectMessage) {
            send((ProjectMessage) msg, clientSessionId);
        } else if (msg instanceof TraceMessage) {
            send((TraceMessage) msg, clientSessionId);
        }
    }

    /**
     * Implement this method to send LogMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(LogMessage msg, String clientSessionId) throws NjamsSdkRuntimeException;

    /**
     * Implement this method to send ProjectMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(ProjectMessage msg, String clientSessionId) throws NjamsSdkRuntimeException;

    /**
     * Implement this method to send TraceMessages
     *
     * @param msg the message to send
     * @throws NjamsSdkRuntimeException NjamsSdkRuntimeException
     */
    protected abstract void send(TraceMessage msg, String clientSessionId) throws NjamsSdkRuntimeException;

    /**
     * Closes this sender. Override to release any resources held by your implementation.
     */
    public void close() {
        // nothing by default
        LOG.debug("Called close on AbstractSender.");
    }

    /**
     * @return true if connectionStatus == ConnectionStatus.CONNECTED
     */
    public boolean isConnected() {
        return getConnectionStatus() == ConnectionStatus.CONNECTED;
    }

    /**
     * @return true if connectionStatus == ConnectionStatus.DISCONNECTED
     */
    public boolean isDisconnected() {
        return getConnectionStatus() == ConnectionStatus.DISCONNECTED;
    }

    /**
     * @return true if connectionStatus == ConnectionStatus.CONNECTING
     */
    public boolean isConnecting() {
        return getConnectionStatus() == ConnectionStatus.CONNECTING;
    }

    /**
     * Injects the callback through which this sender reports connection failures to its owning pool. Called by
     * {@link SenderPool} right after creation. Package-private: not part of the sender SPI.
     *
     * @param failureSink the sink to report failures to.
     */
    void setFailureSink(SenderFailureSink failureSink) {
        this.failureSink = failureSink;
    }

    /**
     * Reports a connection failure that was detected outside a {@code send} call, so the owning
     * {@link SenderPool} can retire this sender and run a single reconnect for its group.
     * <p>
     * Implement this call only if your transport can detect a broken connection asynchronously — for example
     * from a listener callback on a transport-internal thread, with no send in progress. A failure that surfaces
     * from a {@code send} must simply be thrown: the SDK reports it for you.
     *
     * @param cause the failure that was detected; may be {@code null}.
     */
    protected final void notifyConnectionFailure(Exception cause) {
        final SenderFailureSink sink = failureSink;
        if (sink != null) {
            sink.onConnectionFailure(this, cause);
        } else {
            LOG.debug("No failure sink set on sender {}; connection failure not reported.", getName(), cause);
        }
    }

    /**
     * Classifies a failure reported for this sender. Return {@code true} only for a failure that is mere
     * congestion — short-lived and self-healing, likely to succeed again shortly without any manual
     * intervention, such as a transport-level back-pressure signal from an otherwise healthy connection.
     * <p>
     * A connection that is technically established but not usable for its purpose — a rejected destination, a
     * security failure, a malformed request — is <em>not</em> congestion and must not return {@code true} here,
     * even though the transport is nominally "connected".
     * <p>
     * Part of the sender SPI: every implementation must decide this for itself, there is no inherited default.
     * A transport that cannot positively identify congestion must simply return {@code false} — the safe choice,
     * since it keeps the pre-classification behaviour of assuming a real connection problem.
     * <p>
     * Consumed to decide whether a registered {@link SenderRecoveryListener} is notified when the group recovers,
     * and by a transport's own send-retry loop to decide whether a failure under the
     * {@link com.im.njams.sdk.NjamsSettings#PROPERTY_DISCARD_POLICY} {@code onconnectionloss} policy discards the
     * message or keeps applying back pressure. It does not influence retirement or the group's own
     * failed/reconnecting state.
     *
     * @param failure the failure that was reported; may be {@code null}.
     * @return {@code true} only if this transport can positively identify the failure as short-lived congestion;
     *         {@code false} — the safe default — for anything it cannot positively identify as such.
     * @since 6.0.0
     */
    protected abstract boolean isCongestion(Throwable failure);

    /**
     * Classifies a failure reported for this sender. Return {@code true} only if retrying the exact message that
     * failed could never succeed, regardless of the connection's state — for example, a payload the target
     * permanently refuses to accept. A transport that identifies this discards just that one message and leaves
     * the connection untouched, bypassing the configured
     * {@link com.im.njams.sdk.NjamsSettings#PROPERTY_DISCARD_POLICY} entirely: retrying forever cannot help, so
     * discarding is preferable even under a policy that would otherwise never discard.
     * <p>
     * Part of the sender SPI: every implementation must decide this for itself, there is no inherited default.
     * A transport that cannot positively identify a permanent rejection must simply return {@code false} — the
     * safe choice, since retrying might still help.
     *
     * @param failure the failure that was reported; may be {@code null}.
     * @return {@code true} only if this transport can positively identify the message itself as permanently
     *         unsendable; {@code false} — the safe default — for anything it cannot positively identify as such.
     * @since 6.0.0
     */
    protected abstract boolean isMessageRejected(Throwable failure);

}
