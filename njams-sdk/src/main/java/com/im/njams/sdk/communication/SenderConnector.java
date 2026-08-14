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
 * running, that instance is exclusively the connector's and is never reachable through {@link SenderPool#acquire()}.
 * Only once it is connected is it transferred to the pool via {@link SenderPool#onReconnected(AbstractSender)}, so
 * waiters wake with a working sender already available instead of each racing to connect on a worker thread.
 * <p>
 * Startup is re-enterable: any number of later callers awaiting an already-connected group get an immediate
 * success rather than blocking on a spent latch or triggering a second connect. This is what lets several
 * {@link com.im.njams.sdk.Njams} instances share one group (shared communications).
 * <p>
 * At most one of the startup connect or the reconnect loop is ever in flight for a given connector: both
 * {@link #beginConnect()} and {@link #startReconnect(Exception)} check each other's in-flight state (guarded by
 * the same {@link #gate} monitor) before starting a thread, and a failed startup connect hands off to the
 * reconnect loop atomically within the same critical section that clears its own in-flight flag.
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
     * Starts one background connect attempt if the group is not already connected, no connect is in flight, and
     * the group's reconnect loop is not already running. Idempotent and safe to call from any number of threads;
     * at most one of {@code beginConnect()}'s startup thread or {@link #startReconnect(Exception)}'s reconnect
     * thread is ever in flight at once for this connector (see {@link #isReconnectInFlight()}).
     */
    void beginConnect() {
        synchronized (gate) {
            if (coordinator.isGroupConnected() || connectInFlight || isReconnectInFlight()) {
                return;
            }
            connectInFlight = true;
            startupError = null;
            final CountDownLatch latch = new CountDownLatch(1);
            startupLatch = latch;
            try {
                startupThread = new Thread(this::runStartupConnect);
                startupThread.setDaemon(true);
                startupThread.setName("Sender-Startup-" + pool.getSenderName());
                startupThread.start();
            } catch (RuntimeException e) {
                // Preparing/starting the thread failed (e.g. pool.getSenderName() threw) before runStartupConnect()
                // ever got a chance to run and reset connectInFlight itself in its finally block. Without this,
                // connectInFlight would stay true forever and every future beginConnect()/awaitStartup() call on
                // this connector would silently no-op for good.
                LOG.warn("Failed to start the sender group's startup connect thread; the group remains "
                    + "disconnected. A later call will retry.", e);
                connectInFlight = false;
                startupError = e;
                latch.countDown();
            }
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
        Exception failure = null;
        try {
            sender = createSender();
            sender.connect();
            coordinator.markStartupConnected();
            pool.onReconnected(sender);
            sender = null; // ownership transferred
        } catch (Exception e) {
            startupError = e;
            failure = e;
            LOG.debug("Startup connect failed.", e);
        } finally {
            closeQuietly(sender);
            synchronized (gate) {
                // Clear the startup slot and, if warranted, hand off to the reconnect loop in the same critical
                // section: an external beginConnect()/startReconnect() call taking the gate the instant it is
                // released must already see either "connected", "still starting up" or "reconnecting" — never a
                // window where neither is true and it could race a second connect against the handoff.
                connectInFlight = false;
                if (failure != null && coordinator.shouldReconnect()) {
                    startReconnectLocked(failure);
                }
                startupLatch.countDown();
            }
        }
    }

    /**
     * Starts the group's single reconnect loop, unless one is already running, a startup connect is currently in
     * flight, the group is already connected, or a reconnect is not permitted yet (see
     * {@link ConnectionCoordinator#shouldReconnect()}).
     *
     * @param cause the failure that triggered the reconnect; may be {@code null}.
     */
    void startReconnect(Exception cause) {
        synchronized (gate) {
            startReconnectLocked(cause);
        }
    }

    /**
     * Core of {@link #startReconnect(Exception)}; assumes {@code gate} is already held by the caller. Split out so
     * {@link #runStartupConnect()} can hand off from a failed startup connect to the reconnect loop atomically,
     * within the very same critical section that clears {@link #connectInFlight} (see the finally block there).
     */
    private void startReconnectLocked(Exception cause) {
        if (coordinator.isGroupConnected() || !coordinator.shouldReconnect()) {
            return;
        }
        if (connectInFlight || isReconnectInFlight()) {
            return;
        }
        coordinator.beginReconnect();
        try {
            reconnectThread = new Thread(() -> runReconnectLoop(cause));
            reconnectThread.setDaemon(true);
            reconnectThread.setName("Sender-Reconnector-" + pool.getSenderName());
            reconnectThread.start();
        } catch (RuntimeException e) {
            // Mirrors beginConnect()'s guard: if preparing/starting the thread fails (e.g. pool.getSenderName()
            // throws) before it can run and pick the group back up, there is no dangling flag to reset here —
            // isReconnectInFlight() already reads false for a never-started thread — but log it so the failure
            // is visible instead of the group just silently staying disconnected until the next trigger.
            LOG.warn("Failed to start the sender group's reconnect thread; a later trigger will retry.", e);
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

    /** @return {@code true} while the group's reconnect loop is currently running. */
    private boolean isReconnectInFlight() {
        return reconnectThread != null && reconnectThread.isAlive();
    }

    /**
     * Interrupts the startup connect thread only, so a connect still blocked past the startup timeout fails
     * promptly — and, if a reconnect is permitted, hands off to the reconnect loop from its own thread (see
     * {@link #runStartupConnect()}).
     * <p>
     * Deliberately narrower than {@link #cancelReconnect()}, and the two must not be conflated. A startup timeout
     * must never touch a reconnect loop that is already running: that loop is the group's <em>only</em>
     * reconnector, and it has very often been started by this very startup connect failing a moment earlier, or
     * belongs to another {@link com.im.njams.sdk.Njams} sharing this group. Interrupting it destroys the group's
     * sole recovery path, and {@link #startReconnect(Exception)} cannot make up for it: the interrupted thread is
     * still alive for a moment, so {@link #isReconnectInFlight()} refuses to start a replacement and the group is
     * left permanently unable to reconnect. Only a real shutdown wants both threads stopped.
     */
    void cancelStartupConnect() {
        final Thread startup;
        synchronized (gate) {
            startup = startupThread;
        }
        if (startup != null) {
            startup.interrupt();
        }
    }

    /**
     * Interrupts the startup <em>and</em> reconnect threads so a blocking connect is cancelled promptly on
     * shutdown. Only shutdown may use this; a startup timeout must use {@link #cancelStartupConnect()} instead,
     * which explains why.
     */
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
     * Creates one new sender via the factory. {@link CommunicationFactory#getSender()} already initializes the new
     * instance with {@link #settings}, so this must not call {@code init(...)} a second time (that would just
     * re-apply the same settings) — it only wires the failure sink onto the new instance, mirroring
     * {@link SenderPool#create()}'s creation pattern.
     */
    private AbstractSender createSender() {
        final AbstractSender sender = factory.getSender();
        // Every sender the connector publishes ends up serving the group, so it needs the same failure sink
        // SenderPool.create() installs: without it a transport that detects a broken connection asynchronously
        // (JmsSender.onException) has nowhere to report it, and after the first reconnect every pooled sender
        // would be such a sender.
        sender.setFailureSink(pool::reportFailure);
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
