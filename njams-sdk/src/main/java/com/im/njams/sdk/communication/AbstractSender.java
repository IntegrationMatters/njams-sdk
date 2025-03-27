/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import static com.im.njams.sdk.utils.PropertyUtil.getPropertyWithDeprecationWarning;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.common.CommonMessage;
import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Superclass for all Senders. When writing your own Sender, extend this class
 * and overwrite methods, when needed. All Sender will be automatically pooled
 * by the SDK; you must not implement your own connection pooling!
 *
 * @author hsiegeln
 * @version 4.0.6
 */
public abstract class AbstractSender implements Sender {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSender.class);

    protected ConnectionStatus connectionStatus;
    protected DiscardPolicy discardPolicy = DiscardPolicy.DEFAULT;
    protected Properties properties;
    protected boolean hasConnectionFailure = false;
    private Thread reconnector = null;
    private Collection<SenderExceptionListener> exceptionListeners = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final AtomicBoolean hasConnected = new AtomicBoolean(false);

    private static final AtomicInteger connecting = new AtomicInteger(0);

    /**
     * Should be set to true during shutdown
     */
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);

    /**
     * returns a new AbstractSender
     */
    public AbstractSender() {
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }

    @Override
    public void init(Properties properties) {
        this.properties = properties;
        discardPolicy = DiscardPolicy.byValue(getPropertyWithDeprecationWarning(properties,
            NjamsSettings.PROPERTY_DISCARD_POLICY, NjamsSettings.OLD_DISCARD_POLICY));
    }

    /**
     * Set Exception listener with special handling on exceptions.
     *
     * @param exceptionListener the exception listener to add to list
     */
    public void addExceptionListener(SenderExceptionListener exceptionListener) {
        exceptionListeners.add(exceptionListener);
    }

    /**
     * Triggers initial startup of this sender. I.e., the sender tries to connect, and, if not possible, triggers
     * the reconnect loop.<br>
     * This should be called just once for initial connection. Subsequent connection attempts should use the
     * {@link #reconnect(Exception)} implementation.
     */
    public void startup() {
        try {
            connect();
        } catch (Exception e) {
            LOG.error("Startup of sender {} failed. Discard policy is set to '{}'. {}", getName(), discardPolicy,
                getDiscardPolicyMessage(), e);
            reconnect(e);
        }
    }

    private String getDiscardPolicyMessage() {
        switch (discardPolicy) {
        case NONE:
            return "Runtime will be blocked until the connection is established.";
        case DISCARD:
        case ON_CONNECTION_LOSS:
            return "Messages will be discarded until the connection is established. "
                + "This will affect the monitoring with nJAMS.";
        default:
            return "Unkwown discard policy!";
        }
    }

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

    /**
     * Initiates a reconnect thread if {@link #isConnected()} is <code>false</code> and no other reconnect
     * is currently running.
     *
     * @param e the exception that initiated the reconnect
     */
    public synchronized void reconnect(Exception e) {
        if (isConnecting() || isConnected() || shouldShutdown.get()) {
            return;
        }
        if (reconnector != null && reconnector.isAlive()) {
            return;
        }
        reconnector = new Thread(() -> {
            LOG.debug("Start reconnect thread for sender {}", this);
            doReconnect(e);
            LOG.debug("Reconnect thread for sender {} terminated.", this);
        });
        reconnector.setDaemon(true);
        reconnector
            .setName(String.format("Sender-Reconnector-Thread[%s/%d]", getName(), System.identityHashCode(this)));
        reconnector.start();
    }

    /**
     * Implements reconnect behavior. Override this for your own reconnect handling
     * @param ex the exception that initiated the reconnect
     */
    protected synchronized void doReconnect(Exception ex) {
        synchronized (hasConnected) {
            hasConnected.set(false);
            if (LOG.isInfoEnabled() && ex != null) {
                LOG.info("Initialized reconnect, because of: {}", getExceptionWithCauses(ex));
            }
            LOG.debug("{} senders are reconnecting now", connecting.incrementAndGet());
        }
        hasConnectionFailure = true;
        while (!isConnected() && !shouldShutdown.get()) {
            try {
                connect();
                synchronized (hasConnected) {
                    if (!hasConnected.get()) {
                        LOG.info("Reconnected sender {}", getName());
                        hasConnected.set(true);
                    }
                    LOG.debug("{} senders still need to reconnect.", connecting.decrementAndGet());
                }
                hasConnectionFailure = false;
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    return;
                }
            }
        }
    }

    private String getExceptionWithCauses(final Throwable t) {
        Throwable current = t;
        StringBuilder sb = new StringBuilder();
        while (current != null) {
            if (sb.length() > 1) {
                sb.append(", caused by: ");
            }
            sb.append(current.toString());
            current = current.getCause();
        }
        return sb.toString();
    }


    protected synchronized void setConnectionStatus(ConnectionStatus newConnectionStatus) {
        this.connectionStatus = newConnectionStatus;
    }

    protected synchronized ConnectionStatus getConnectionStatus() {
        return this.connectionStatus;
    }

    /**
     * Send the given message. This method automatically applies the
     * discardPolicy onConnectionLoss, if set
     *
     * @param msg the message to send
     */
    @Override
    public void send(CommonMessage msg, String clientSessionId) {
        LOG.trace("Sending message {}, state={}", msg, getConnectionStatus());
        // do this until message is sent or discard policy onConnectionLoss is satisfied
        boolean isSent = false;
        do {
            if (isConnected()) {
                try {
                    if (msg instanceof LogMessage) {
                        send((LogMessage) msg, clientSessionId);
                    } else if (msg instanceof ProjectMessage) {
                        send((ProjectMessage) msg, clientSessionId);
                    } else if (msg instanceof TraceMessage) {
                        send((TraceMessage) msg, clientSessionId);
                    }
                    isSent = true;
                    break;
                } catch (Exception e) {
                    for (SenderExceptionListener listener : exceptionListeners) {
                        listener.onException(e, msg);
                    }
                    onException(e);
                }
            }
            // if connecting, we're effectively disconnected
            if (isDisconnected() || isConnecting()) {
                // discard message, if onConnectionLoss is used
                isSent = discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS;
                if (isSent) {
                    DiscardMonitor.discard();
                    LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                    break;
                }
            }
            // wait for reconnect
            if (isConnecting()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                // trigger reconnect
                onException(new IllegalStateException("Not connected"));
            }
        } while (!isSent && !Thread.currentThread().isInterrupted() && !shouldShutdown.get());
    }

    /**
     * used to implement your exception handling for this sender. Is called, if
     * sending of a message fails. It will automatically close any try to
     * reconnect the connection; override this method for your own handling
     *
     * @param exception NjamsSdkRuntimeException
     */
    protected void onException(Exception exception) {
        // close the existing connection
        close();
        reconnect(exception);
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

    @Override
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
     * Set this value to true during shutdown to stop the reconnecting thread
     *
     * @param shutdown if the Sender is in shutdown state
     */
    public void setShouldShutdown(boolean shutdown) {
        shouldShutdown.set(shutdown);
    }

    /**
     * Returns <code>true</code> in case connection failed or could not be established initially.
     * @return <code>true</code> only in case of connection failure.
     */
    public boolean hasConnectionFailure() {
        return hasConnectionFailure;
    }

}
