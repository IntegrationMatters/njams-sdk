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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns the startup connect and the single reconnect loop for one sender group. Exactly one instance exists per
 * {@link SenderPool}, which creates and owns it.
 * <p>
 * The connector works on its own private {@link AbstractSender}: while a startup connect or a reconnect loop is
 * running, that instance is exclusively the connector's and is never reachable through {@link SenderPool#get()}.
 * Only once it is connected is it transferred to the pool via {@link SenderPool#onReconnected(AbstractSender)}, so
 * waiters wake with a working sender already available instead of each racing to connect on a worker thread.
 * <p>
 * Startup is re-enterable: any number of later callers awaiting an already-connected group get an immediate
 * success rather than blocking on a spent latch or triggering a second connect. This is what lets several
 * {@link com.im.njams.sdk.Njams} instances share one group (shared communications).
 * <p>
 * Internal SDK infrastructure — not public API.
 */
class SenderConnector {

    private static final long RECONNECT_INTERVAL_MS = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(SenderConnector.class);

    private final CommunicationFactory factory;
    private final ConnectionCoordinator coordinator;
    private final SenderPool pool;
    private final ClientSettings settings;

    /** Guards startup/reconnect thread creation and the latch handoff below. */
    private final Object gate = new Object();
    private boolean connectInFlight = false;
    private CountDownLatch startupLatch = new CountDownLatch(0);
    private Thread startupThread;
    private Thread reconnectThread;
    private volatile Exception startupError;

    SenderConnector(CommunicationFactory factory, ConnectionCoordinator coordinator, SenderPool pool,
        ClientSettings settings) {
        this.factory = factory;
        this.coordinator = coordinator;
        this.pool = pool;
        this.settings = settings;
    }

    /**
     * Starts one background connect attempt if the group is not already connected and no connect is in flight.
     * Idempotent and safe to call from any number of threads.
     */
    void beginConnect() {
        synchronized (gate) {
            if (coordinator.isGroupConnected() || connectInFlight) {
                return;
            }
            connectInFlight = true;
            startupError = null;
            startupLatch = new CountDownLatch(1);
            startupThread = new Thread(this::runStartupConnect);
            startupThread.setDaemon(true);
            startupThread.setName("Sender-Startup-" + pool.getSenderName());
            startupThread.start();
        }
    }

    /**
     * Waits up to {@code timeoutMs} for the group to be connected, starting a connect if none is running.
     * Returns immediately for a late caller against an already-connected group.
     *
     * @param timeoutMs maximum time to wait, in milliseconds.
     * @return {@code true} iff the group is connected.
     */
    boolean awaitStartup(long timeoutMs) {
        if (coordinator.isGroupConnected()) {
            return true;
        }
        final CountDownLatch latch;
        synchronized (gate) {
            beginConnect();
            latch = startupLatch;
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return coordinator.isGroupConnected() && startupError == null;
    }

    private void runStartupConnect() {
        AbstractSender sender = null;
        try {
            sender = createSender();
            sender.connect();
            coordinator.markStartupConnected();
            pool.onReconnected(sender);
            sender = null; // ownership transferred
        } catch (Exception e) {
            startupError = e;
            LOG.debug("Startup connect failed.", e);
            if (coordinator.shouldReconnect()) {
                startReconnect(e);
            }
        } finally {
            closeQuietly(sender);
            synchronized (gate) {
                connectInFlight = false;
                startupLatch.countDown();
            }
        }
    }

    /**
     * Starts the group's single reconnect loop, unless one is already running, the group is already connected, or
     * a reconnect is not permitted yet (see {@link ConnectionCoordinator#shouldReconnect()}).
     *
     * @param cause the failure that triggered the reconnect; may be {@code null}.
     */
    void startReconnect(Exception cause) {
        synchronized (gate) {
            if (coordinator.isGroupConnected() || !coordinator.shouldReconnect()) {
                return;
            }
            if (reconnectThread != null && reconnectThread.isAlive()) {
                return;
            }
            coordinator.beginReconnect();
            reconnectThread = new Thread(() -> runReconnectLoop(cause));
            reconnectThread.setDaemon(true);
            reconnectThread.setName("Sender-Reconnector-" + pool.getSenderName());
            reconnectThread.start();
        }
    }

    private void runReconnectLoop(Exception cause) {
        if (LOG.isInfoEnabled() && cause != null) {
            LOG.info("Initialized reconnect, because of: {}", getExceptionWithCauses(cause));
        }
        while (!coordinator.isGroupConnected() && !coordinator.shouldShutdown()) {
            AbstractSender sender = null;
            try {
                sender = createSender();
                sender.connect();
                if (coordinator.markConnected()) {
                    LOG.info("Reconnected sender {}", sender.getName());
                }
                pool.onReconnected(sender);
                return;
            } catch (Exception e) {
                closeQuietly(sender);
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Interrupts the startup and reconnect threads so a blocking connect is cancelled promptly on shutdown. */
    void cancelReconnect() {
        final Thread startup;
        final Thread reconnect;
        synchronized (gate) {
            startup = startupThread;
            reconnect = reconnectThread;
        }
        if (startup != null) {
            startup.interrupt();
        }
        if (reconnect != null) {
            reconnect.interrupt();
        }
    }

    /**
     * Creates and connects-up one new sender via the factory. {@link CommunicationFactory#getSender()} already
     * initializes the new instance with {@link #settings}, so this must not call {@code init(...)} a second time
     * (that would just re-apply the same settings) — it only wires the shared {@link #coordinator} onto the new
     * instance, mirroring {@link SenderPool#create()}'s creation pattern.
     */
    private AbstractSender createSender() {
        final AbstractSender sender = factory.getSender();
        sender.setConnectionCoordinator(coordinator);
        return sender;
    }

    private void closeQuietly(AbstractSender sender) {
        if (sender == null) {
            return;
        }
        try {
            sender.close();
        } catch (Exception e) {
            LOG.debug("Failed to close a sender after an unsuccessful connect.", e);
        }
    }

    private static String getExceptionWithCauses(final Throwable t) {
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
}
