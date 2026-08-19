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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Pool for {@link AbstractSender} implementations, and the owner of connection-failure handling for one sender
 * group.
 * <p>
 * <b>Borrowing.</b> {@link #acquire()} hands out a <em>connected</em> sender — an idle one from the pool if
 * available, otherwise a freshly created and connected one — and {@link #release(AbstractSender)} returns it. The
 * pool imposes no upper limit of its own, so in isolation the number of senders could grow without bound. In
 * practice it cannot, because the pool is only ever borrowed from by the bounded
 * {@link java.util.concurrent.ThreadPoolExecutor} in {@link NjamsSender}. That executor runs at most
 * {@link com.im.njams.sdk.NjamsSettings#PROPERTY_MAX_SENDER_THREADS} worker threads concurrently, and each worker
 * acquires a sender, sends, and releases it before picking up the next task. The number of senders simultaneously
 * checked out therefore never exceeds the executor's maximum thread count; the rest are recycled. In other words,
 * the pool is <em>virtually</em> bounded to {@code maxSenderThreads} by its single caller.
 * <p>
 * <b>Failure handling and retirement.</b> A sender that discovers a broken connection reports it through
 * {@link #reportFailure(AbstractSender, Exception)}. The first report of an outage <em>elects</em> exactly one
 * reconnector: the pool flips into the reconnecting state, notifies its exception listeners once for the group
 * (not once per message), and hands the reconnect to its single {@link SenderConnector}. Every sender that is
 * checked out at that moment is <em>retired</em> — it stays with its borrowing thread until that thread calls
 * {@link #release(AbstractSender)}, which then closes it instead of recycling it. This is what keeps a broken
 * sender from being handed to the next caller without ever closing a sender under a foreign thread. Later reports
 * for the same outage are absorbed, so a burst of failing worker threads produces one reconnect, not one per
 * thread. When the connector has a working sender it publishes it via
 * {@link #onReconnected(AbstractSender, boolean)},
 * which clears the failure state and wakes everyone parked in {@link #acquire()}.
 *
 * @author hsiegeln
 */
public class SenderPool {
    private static final Logger LOG = LoggerFactory.getLogger(SenderPool.class);

    private final CommunicationFactory factory;
    private final ConnectionCoordinator coordinator;
    private final SenderConnector connector;
    private final DiscardPolicy discardPolicy;

    /**
     * Guards every mutation of the sets and flags below, and is the monitor blocked {@link #acquire()} callers
     * park on. Deliberately a dedicated object rather than the pool's own monitor, so a parked {@code acquire()}
     * cannot stall {@link #release(AbstractSender)}, {@link #reportFailure(AbstractSender, Exception)} or
     * shutdown by holding {@code this}.
     */
    private final Object lock = new Object();

    private final Set<AbstractSender> locked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractSender> unlocked = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Senders that were checked out when the group failed. Always a subset of {@link #locked}: they remain
     * borrowed until their thread releases them, and are then closed instead of recycled.
     */
    private final Set<AbstractSender> retired = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Only ever touched under {@link #lock}, and fired from a copy taken under {@link #lock}. Keeps its
     * {@link IdentityHashMap} identity semantics — do not switch to a {@code ConcurrentHashMap}-backed set, that
     * would silently change listener comparison from identity to {@code equals}.
     */
    private final Collection<SenderExceptionListener> exceptionListeners =
        Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Identity semantics for the same reason as {@link #exceptionListeners}: a shared receiver registered by
     * several {@code Njams} instances must be signalled once, not once per instance. Add-only would leak a
     * stopped instance's receiver into a JVM-wide shared group, hence {@link #removeSenderRecoveryListener}.
     */
    private final Collection<SenderRecoveryListener> recoveryListeners =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private boolean reconnecting = false;
    /**
     * Whether the failure that opened the current outage indicated a broken connection, as classified by the
     * failing sender. Only ever touched under {@link #lock}. Consumed by
     * {@link #onReconnected(AbstractSender, boolean)} and by nothing else — it must not influence retirement,
     * message fate or the discard policy (SDK-474 owns those).
     */
    private boolean outageIndicatesBrokenConnection = true;
    /**
     * Whether the publish that made the group healthy again followed at least one failed connect attempt, i.e.
     * whether the endpoint was demonstrably unreachable. Only ever touched under {@link #lock}.
     */
    private boolean recoveredAfterFailedConnectAttempt = false;
    /** Read without the lock by {@link #isConnectionFailure()} on the executor's rejection path. */
    private volatile boolean failed = false;
    /** Set by {@link #declareShutdown()}: blocks creation of new senders. */
    private boolean shutdown = false;
    /** Set by {@link #beginShutdown()}: refuses and wakes {@link #acquire()} callers, but still allows creation. */
    private boolean draining = false;
    /** Counts group-failure notifications, i.e. how often a reconnector was elected. Guarded by {@link #lock}. */
    private int listenerFireCount = 0;
    /** The sender the connector published most recently, for {@link #awaitPublishedSenderForTest()}. */
    private AbstractSender lastPublished;

    /**
     * Creates a pool with its own dedicated {@link ConnectionCoordinator} and no settings, so the default
     * {@link DiscardPolicy} applies.
     *
     * @param factory the factory used to create new senders
     */
    public SenderPool(CommunicationFactory factory) {
        this(factory, new ConnectionCoordinator());
    }

    /**
     * Creates a pool sharing the given coordinator, with no settings, so the default {@link DiscardPolicy}
     * applies.
     *
     * @param factory     the factory used to create new senders
     * @param coordinator the connection state shared by this pool's sender group
     */
    public SenderPool(CommunicationFactory factory, ConnectionCoordinator coordinator) {
        this(factory, coordinator, ClientSettings.from(new HashMap<>()));
    }

    /**
     * Creates a pool sharing the given coordinator and reading its group-level configuration from
     * {@code settings}.
     *
     * @param factory     the factory used to create new senders
     * @param coordinator the connection state shared by this pool's sender group
     * @param settings    the client settings; the discard policy is read from them once, here, and never per
     *                    message (the runtime path must not read settings live)
     */
    public SenderPool(CommunicationFactory factory, ConnectionCoordinator coordinator, ClientSettings settings) {
        this.factory = factory;
        this.coordinator = coordinator;
        // Read once at construction: acquire() applies this per message and must never touch the settings there.
        discardPolicy = DiscardPolicy.byValue(settings.getProperty(NjamsSettings.PROPERTY_DISCARD_POLICY));
        connector = new SenderConnector(factory, coordinator, this, settings);
    }

    /**
     * Add a listener that is called whenever the group's connection fails. The listener is notified once per
     * outage by {@link #reportFailure(AbstractSender, Exception)}, not once per failed message.
     *
     * @param listener The listener to add
     */
    public void addSenderExceptionListener(SenderExceptionListener listener) {
        synchronized (lock) {
            exceptionListeners.add(listener);
        }
    }

    /**
     * Adds a listener notified once per outage, when the group's connection is re-established after at least one
     * failed connect attempt.
     *
     * @param listener the listener to add.
     */
    void addSenderRecoveryListener(SenderRecoveryListener listener) {
        synchronized (lock) {
            recoveryListeners.add(listener);
        }
    }

    /**
     * Removes a previously added recovery listener. Required because a shared group outlives the individual
     * clients using it.
     *
     * @param listener the listener to remove; unknown listeners are ignored.
     */
    void removeSenderRecoveryListener(SenderRecoveryListener listener) {
        synchronized (lock) {
            recoveryListeners.remove(listener);
        }
    }

    /**
     * The group's connection-failure flag: {@code true} from the moment a failure is reported until the connector
     * publishes a working sender again. Feeds {@link MaxQueueLengthHandler}'s {@code ON_CONNECTION_LOSS} branch.
     * <p>
     * It is a single group-level flag on purpose. Deriving it by scanning the pooled senders would go blind
     * exactly when it matters: on failure the broken senders are retired and closed, so a scan over what is left
     * in the pool would report a healthy group while the reconnect is still running.
     *
     * @return {@code true} while the group is known to be disconnected.
     */
    public boolean isConnectionFailure() {
        return failed;
    }

    /** Permits reconnect before the first successful connect for this group (startup {@code reconnect} policy). */
    public void allowReconnectBeforeConnected() {
        coordinator.allowReconnectBeforeConnected();
    }

    /**
     * Starts the group's initial connect in the background, so a slow connect overlaps application setup.
     * Idempotent and thread-safe: the connector starts at most one connect for the group.
     */
    void beginConnect() {
        connector.beginConnect();
    }

    /**
     * Waits up to {@code timeoutMs} for the group's initial connect to complete, starting one if none is running.
     * A caller arriving late against an already-connected group succeeds immediately.
     *
     * @param timeoutMs maximum time to wait, in milliseconds.
     * @return {@code true} iff the group is connected.
     */
    boolean awaitStartup(long timeoutMs) {
        return connector.awaitStartup(timeoutMs);
    }

    /**
     * Abandons an initial connect that did not complete within the startup timeout and leaves the group retrying in
     * the background (startup fail-behavior {@code reconnect}).
     * <p>
     * Cancelling comes first, and both cancel and (re)start are needed, for different cases.
     * {@link SenderConnector#cancelStartupConnect()} interrupts a startup connect that is <em>still blocked</em>,
     * so it fails promptly and — being a failed connect with the group disconnected — hands off to the reconnect
     * loop from its own thread (see {@code SenderConnector.runStartupConnect}). The (re)start below covers the
     * other case, where the initial connect already failed fast and left no thread to interrupt; it is idempotent
     * when a reconnect is already running.
     * <p>
     * The cancel is deliberately the <em>startup-only</em> one, never {@link SenderConnector#cancelReconnect()}.
     * By the time a startup timeout is handled, the failed startup connect has usually already handed off to a
     * reconnect loop, and that loop is the group's only reconnector — cancelling it here killed the very recovery
     * this method exists to guarantee, and the restart below could not replace it while the interrupted thread was
     * still alive. See {@code SenderConnector.cancelStartupConnect()}.
     * <p>
     * The group is then flipped into the failed/reconnecting state through the same election path a failing sender
     * takes, just without a failing sender to retire. This is not cosmetic bookkeeping: the pool's own
     * {@code reconnecting} flag is what stops {@link #acquire()} from creating and connecting a sender of its own.
     * Left healthy-looking, a worker dispatching a message here would take {@link #lock} and start a
     * <em>second</em> connect, competing with the connector's — the very connect storm this design removes — and
     * it would hold the lock for the whole blocking connect, so nothing in the group, {@link #beginShutdown()}
     * included, could make progress. Flipping the flag also makes {@link #isConnectionFailure()} report the truth
     * while a startup-driven reconnect is running, which is what {@link MaxQueueLengthHandler}'s
     * {@code ON_CONNECTION_LOSS} branch needs.
     *
     * @param timeoutMs the startup timeout that elapsed, for the reconnect cause's message.
     */
    void restartConnectInBackground(long timeoutMs) {
        final Exception cause = new NjamsSdkRuntimeException(
            "Startup connect did not complete within " + timeoutMs + " ms; reconnecting in background");
        connector.cancelStartupConnect();
        List<AbstractSender> toDestroy = Collections.emptyList();
        List<SenderExceptionListener> listeners = null;
        synchronized (lock) {
            // The connected check closes the narrow race where the startup connect completes between the timeout
            // expiring and this call, which must not fail a group that is healthy after all.
            if (!reconnecting && !coordinator.isGroupConnected()) {
                toDestroy = failGroup(true);
                listeners = new ArrayList<>(exceptionListeners);
            }
        }
        toDestroy.forEach(this::destroy);
        if (listeners != null) {
            electReconnector(cause, listeners);
        } else {
            // Already reconnecting (or connected): no new election, but still kick the connector, which is what
            // recovers a group whose reconnect thread was cancelled. No-op while one is running.
            connector.startReconnect(cause);
        }
    }

    /**
     * Cancels the group's in-progress startup connect without shutting the group down. Used by the fail-fast
     * startup policy, where a connect that has not completed in time must not keep running.
     * <p>
     * It cancels the startup connect <em>only</em>. This client giving up on its own startup says nothing about
     * the group: under shared communications a sibling that started under the {@code reconnect} policy may have a
     * reconnect running that it still depends on, and the group has just the one reconnector. Tearing the group
     * down is {@link #beginShutdown()}'s job, not this one's.
     */
    void cancelConnect() {
        connector.cancelStartupConnect();
    }

    /**
     * Creates a new, not-yet-connected sender.
     *
     * @return the new sender, or {@code null} once {@link #declareShutdown()} has been called.
     */
    protected AbstractSender create() {
        if (shutdown) {
            return null;
        }
        final AbstractSender sender = factory.getSender();
        sender.setFailureSink(this::reportFailure);
        return sender;
    }

    /**
     * Closes one sender and drops it. Never call this while holding {@link #lock}: a real transport's
     * {@code close()} can block, which would stall the whole group's acquire/release traffic.
     * <p>
     * Deliberately does <em>not</em> touch the group's shutdown state: retiring a single broken sender must not
     * abort the group's elected reconnect. Group shutdown is set explicitly by {@link #beginShutdown()},
     * {@link #declareShutdown()} and {@link #shutdown()}.
     */
    private void destroy(AbstractSender sender) {
        try {
            sender.close();
        } catch (Exception e) {
            LOG.error("Couldn't close {}", sender.getClass().getSimpleName());
        }
    }

    /**
     * Shuts the group down for good: stops reconnecting, releases anyone parked in {@link #acquire()}, and closes
     * every sender this pool still holds. Correct standalone, i.e. also when called without a preceding
     * {@link #beginShutdown()}.
     */
    public void shutdown() {
        coordinator.setShouldShutdown(true);
        expireAll();
    }

    /**
     * Hands out a connected sender, applying the group's discard policy while a reconnect is in progress.
     * <p>
     * A healthy group still serves callers once {@link #beginShutdown()} has run, so in-flight sends can complete
     * while the executor drains. Only a caller that would have to <em>wait</em> for a reconnect is refused then:
     * that reconnect is being cancelled, so waiting for it would be pointless. Once {@link #declareShutdown()}
     * has committed to teardown, every caller is refused.
     * <p>
     * Creating a brand-new sender (no idle one pooled) reserves it under {@link #lock} via {@link #create()} but
     * connects it <em>outside</em> the lock (decision B2, reversed): at most {@code maxSenderThreads} callers ever
     * reach this path, and only while the group is healthy, so letting their connects run concurrently is ordinary
     * pool growth, not the uncoordinated per-sender reconnect storm this design removes — that storm is specific
     * to an actual outage, which stays owned end-to-end by {@link SenderConnector}'s single reconnect loop and is
     * untouched by this. Not holding the lock here means a slow-but-reachable endpoint can no longer stall every
     * other {@link #acquire()}/{@link #release(AbstractSender)} call in the group for the duration of one connect.
     *
     * @return a CONNECTED sender, or {@code null} if the group is shutting down or the discard policy chose to
     *         drop the message rather than wait.
     */
    AbstractSender acquire() {
        final boolean discardsOnFailure =
            discardPolicy == DiscardPolicy.DISCARD || discardPolicy == DiscardPolicy.ON_CONNECTION_LOSS;
        while (true) {
            // Side effects of a failure detected inside the monitor. Collected under the lock, acted on only
            // after it is released: closing a sender, notifying listeners or starting the reconnect thread while
            // holding the lock would stall (or re-enter) the whole group.
            AbstractSender halfBuilt = null;
            Exception connectFailure = null;
            List<AbstractSender> toDestroy = null;
            List<SenderExceptionListener> toNotify = null;
            final AbstractSender toConnect;
            synchronized (lock) {
                if (shutdown) {
                    return null;
                }
                if (reconnecting) {
                    if (discardsOnFailure) {
                        DiscardMonitor.discard();
                        LOG.debug("Applying discard policy [{}]. Message discarded.", discardPolicy);
                        return null;
                    }
                    if (draining) {
                        // Refuse rather than park: the reconnect this caller would wait for is being cancelled.
                        // Deliberately not counted as a discard — the drop is caused by shutdown, not by the
                        // configured discard policy, and conflating the two would misreport the discard metric.
                        LOG.debug("Group is shutting down while reconnecting; dropping the message without waiting.");
                        return null;
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    // Re-evaluate: the group may now be connected, or shutting down.
                    continue;
                }
                final AbstractSender pooled = takePooled();
                if (pooled != null) {
                    return pooled;
                }
                // Reserves the slot: this sender exists but is in neither `locked` nor `unlocked` yet, so nothing
                // else in the pool can see or touch it while it connects below, outside the lock.
                toConnect = create();
                if (toConnect == null) {
                    // Shutdown was declared while we were here.
                    return null;
                }
            }
            try {
                toConnect.connect();
            } catch (Exception e) {
                connectFailure = e;
            }
            synchronized (lock) {
                if (shutdown) {
                    // Torn down while we were connecting outside the lock; don't publish it, just drop it.
                    halfBuilt = toConnect;
                } else if (connectFailure != null) {
                    // The group is broken, not just this attempt: fail it inline rather than re-entering
                    // reportFailure(), which would deadlock on the lock we already hold.
                    halfBuilt = toConnect;
                    if (!reconnecting) {
                        // true: toConnect never finished connecting, so there is no sender to ask - the same
                        // "initial connect did not complete" evidence restartConnectInBackground(long) counts as
                        // a broken connection.
                        toDestroy = failGroup(true);
                        toNotify = new ArrayList<>(exceptionListeners);
                    }
                    // else: a concurrent sibling's create() already failed the group first - absorbed.
                } else if (reconnecting) {
                    // A sibling's connect failed while ours independently succeeded. The group is already in
                    // recovery via the elected reconnector, and only its onReconnected(...) publish is allowed to
                    // clear `reconnecting` (the single-reconnector invariant this whole design rests on) - so this
                    // redundant, otherwise-healthy sender is dropped rather than published into a failed group.
                    halfBuilt = toConnect;
                } else {
                    locked.add(toConnect);
                    LOG.debug("Created and connected sender: {}", toConnect);
                    return toConnect;
                }
            }
            if (halfBuilt != null) {
                destroy(halfBuilt);
            }
            if (toNotify != null) {
                toDestroy.forEach(this::destroy);
                electReconnector(connectFailure, toNotify);
            }
            // Loop round and re-evaluate under the new (reconnecting) state.
        }
    }

    /**
     * Retires the given sender and, if this is the first report for the current outage, elects the group's single
     * reconnector and notifies the exception listeners once.
     *
     * @param sender the sender that hit the failure; it is closed, never recycled.
     * @param cause  the failure, passed on to the listeners and the reconnect loop.
     */
    void reportFailure(AbstractSender sender, Exception cause) {
        // Classified outside the lock: this calls into transport code, which must never run while the group's
        // lock is held.
        final boolean brokenConnection = sender == null || classifyQuietly(sender, cause);
        List<AbstractSender> toDestroy = Collections.emptyList();
        List<SenderExceptionListener> listeners = null;
        synchronized (lock) {
            locked.remove(sender);
            retired.remove(sender);
            if (!reconnecting) {
                toDestroy = failGroup(brokenConnection);
                listeners = new ArrayList<>(exceptionListeners);
            }
        }
        destroy(sender);
        toDestroy.forEach(this::destroy);
        if (listeners != null) {
            electReconnector(cause, listeners);
        }
    }

    /**
     * Returns a borrowed sender: back into the pool if it is still healthy, closed if it was retired by a failure
     * or the group is shutting down.
     *
     * @param sender the sender previously handed out by {@link #acquire()}.
     */
    void release(AbstractSender sender) {
        final boolean discard;
        synchronized (lock) {
            locked.remove(sender);
            discard = retired.remove(sender) || shutdown;
            if (!discard) {
                unlocked.add(sender);
            }
        }
        if (discard) {
            destroy(sender);
        }
    }

    /**
     * Publishes a sender the {@link SenderConnector} has just connected: the group is healthy again, the sender
     * becomes available to the next caller, and everyone parked in {@link #acquire()} is woken.
     *
     * @param connected the freshly connected sender; ownership transfers to this pool.
     * @param afterFailedConnectAttempt whether the connector had to retry before this connect succeeded, which is
     *         the only reliable evidence that the endpoint really was unreachable.
     */
    void onReconnected(AbstractSender connected, boolean afterFailedConnectAttempt) {
        final List<SenderRecoveryListener> toNotify;
        synchronized (lock) {
            // Both halves of the gate, evaluated while the outage's state is still intact: the failure that
            // opened it looked like a broken connection, AND the connector really could not reach the endpoint.
            final boolean recoveredFromOutage = afterFailedConnectAttempt && outageIndicatesBrokenConnection;
            recoveredAfterFailedConnectAttempt = afterFailedConnectAttempt;
            reconnecting = false;
            failed = false;
            lastPublished = connected;
            unlocked.add(connected);
            lock.notifyAll();
            toNotify = recoveredFromOutage ? new ArrayList<>(recoveryListeners) : null;
        }
        if (toNotify != null) {
            notifyRecovered(toNotify);
        }
    }

    /**
     * Signals the listeners <em>outside</em> {@link #lock}: a listener cycles a receiver's connection, which must
     * never run while the group's lock is held.
     */
    private void notifyRecovered(List<SenderRecoveryListener> listeners) {
        for (SenderRecoveryListener listener : listeners) {
            try {
                listener.onSenderGroupRecovered();
            } catch (RuntimeException | Error e) {
                // One misbehaving listener must not stop the others from learning about the recovery.
                LOG.error("Sender recovery listener {} failed while handling the group's reconnect.",
                    listener.getClass().getName(), e);
            }
        }
    }

    /**
     * Flips the group into the failed/reconnecting state and retires everything currently checked out. Must be
     * called with {@link #lock} held and only while {@code !reconnecting}.
     *
     * @param brokenConnection whether the failure opening this outage indicated a broken connection.
     * @return the idle senders the caller must close <em>after</em> releasing the lock.
     */
    private List<AbstractSender> failGroup(boolean brokenConnection) {
        outageIndicatesBrokenConnection = brokenConnection;
        reconnecting = true;
        failed = true;
        // A new outage invalidates whatever the previous reconnect published.
        lastPublished = null;
        listenerFireCount++;
        // Record the loss on the coordinator while still holding lock. It is required at all because
        // SenderConnector.startReconnect refuses a group it believes to be connected, and it belongs here rather
        // than after the lock releases: otherwise there is a window - spanning the destroy(...) calls, which can
        // block inside a real transport's close() - where this pool is already reconnecting while
        // coordinator.isGroupConnected() still reads true, so a sibling Njams sharing the group would get a stale
        // "connected" answer from SenderConnector.awaitStartup(...). Safe under the lock: the coordinator
        // synchronizes on its own monitor and never calls back into the pool.
        coordinator.beginReconnect();
        final List<AbstractSender> toDestroy = drain(unlocked);
        retired.addAll(locked);
        return toDestroy;
    }

    /**
     * Asks the sender whether the failure was mere congestion. A classifier that throws is treated as "cannot
     * tell", so a broken implementation can never make the group behave differently than it did before
     * classification existed.
     */
    private boolean classifyQuietly(AbstractSender sender, Exception cause) {
        try {
            return !sender.isCongestion(cause);
        } catch (RuntimeException e) {
            LOG.debug("Sender {} failed to classify a connection failure; assuming a broken connection.",
                sender.getName(), e);
            return true;
        }
    }

    /**
     * Second half of failing the group, run <em>outside</em> {@link #lock}: starts the group's single reconnect
     * loop and then notifies the listeners once for the outage.
     */
    private void electReconnector(Exception cause, List<SenderExceptionListener> listeners) {
        // Start the reconnect BEFORE notifying anyone. failGroup() has already latched reconnecting/failed, and
        // only onReconnected(...) clears them, so a listener throwing ahead of this call would brick the group for
        // good: every later acquire() would park forever or discard forever. Unlike the legacy per-sender path,
        // this latch does not self-heal on the next send.
        connector.startReconnect(cause);
        for (SenderExceptionListener listener : listeners) {
            try {
                // A per-outage notification has no single message, so the message argument is null: "the group
                // failed", not "this message failed".
                listener.onException(cause, null);
            } catch (RuntimeException e) {
                // One misbehaving listener must not stop the others from learning about the outage.
                LOG.error("Sender exception listener {} failed while handling the group's connection failure.",
                    listener.getClass().getName(), e);
            }
        }
    }

    /** Takes an idle sender and marks it checked out. Must be called with {@link #lock} held. */
    private AbstractSender takePooled() {
        final Iterator<AbstractSender> it = unlocked.iterator();
        if (!it.hasNext()) {
            return null;
        }
        final AbstractSender sender = it.next();
        it.remove();
        locked.add(sender);
        LOG.trace("Reusing pooled sender {} (locked={}, unlocked={})", sender, locked.size(), unlocked.size());
        return sender;
    }

    /** Copies the set's contents into a list and empties the set. Must be called with {@link #lock} held. */
    private List<AbstractSender> drain(Set<AbstractSender> senders) {
        final List<AbstractSender> drained = new ArrayList<>(senders);
        senders.clear();
        return drained;
    }

    /**
     * Derives a display name for this pool's sender group from the factory by creating (and discarding) one
     * throwaway instance. Used by {@link SenderConnector} for its thread names; not the pool's real
     * sender-creation path.
     */
    String getSenderName() {
        return factory.getSender().getName();
    }

    /**
     * Begins shutdown for the group: sets the coordinator's shutdown flag and cancels any in-progress
     * reconnect/startup threads, so a failing final send during the executor drain does not spawn a reconnect.
     * It also wakes every {@link #acquire()} caller parked on a reconnect and hands it {@code null}, so nobody
     * stays blocked waiting for a reconnect that has just been cancelled.
     * <p>
     * Unlike {@link #declareShutdown()} this does <em>not</em> block new-sender creation or refuse an
     * {@link #acquire()} against a still-healthy group, so in-flight sends can complete while the executor drains.
     * That is why the wake uses its own {@code draining} flag rather than the {@code shutdown} flag
     * {@link #declareShutdown()} sets: the two mean different things and must not share a field.
     */
    public void beginShutdown() {
        LOG.debug("Beginning sender group shutdown; cancelling reconnects.");
        coordinator.setShouldShutdown(true);
        synchronized (lock) {
            // Set before cancelling, so a waiter woken by a reconnect thread that is finishing concurrently
            // already sees the drain instead of picking the group back up.
            draining = true;
            lock.notifyAll();
        }
        connector.cancelReconnect();
    }

    /**
     * Completes shutdown: from here on no new sender is created or handed out, and every sender still checked out
     * is closed when its borrower releases it.
     */
    public void declareShutdown() {
        synchronized (lock) {
            shutdown = true;
            draining = true;
            lock.notifyAll();
        }
        coordinator.setShouldShutdown(true);
    }

    private void expireAll() {
        final List<AbstractSender> all;
        synchronized (lock) {
            // Also set here, not only in beginShutdown()/declareShutdown(): shutdown() is public API and may be
            // called standalone, and without this the notifyAll() below would wake parked acquire() callers only
            // for them to find neither draining nor a completed reconnect, and park again forever.
            draining = true;
            // retired is a subset of locked, so allSenders() already covers it.
            all = allSenders();
            locked.clear();
            unlocked.clear();
            retired.clear();
            lock.notifyAll();
        }
        all.forEach(this::destroy);
    }

    /** A snapshot of every sender this pool holds. Must be called with {@link #lock} held. */
    private List<AbstractSender> allSenders() {
        final List<AbstractSender> all = new ArrayList<>(unlocked.size() + locked.size());
        all.addAll(unlocked);
        all.addAll(locked);
        return all;
    }

    /**
     * Test-only accessor (hence the name): waits for the connector to publish a connected sender through
     * {@link #onReconnected(AbstractSender, boolean)} and returns it. Uses the pool's own lock/wait pair rather than
     * polling. Narrowly named and package-private in preference to widening real API or using reflection.
     *
     * @return the most recently published sender, or {@code null} if none was published within 5 seconds.
     */
    AbstractSender awaitPublishedSenderForTest() {
        final long deadline = System.currentTimeMillis() + 5000;
        synchronized (lock) {
            while (lastPublished == null) {
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return lastPublished;
        }
    }

    /**
     * Test-only accessor (hence the name) over the group-failure notification counter, i.e. how often a
     * reconnector was elected. Narrowly named and package-private in preference to widening real API.
     *
     * @return the number of times the exception listeners were notified of a group failure.
     */
    int exceptionListenerFireCountForTest() {
        synchronized (lock) {
            return listenerFireCount;
        }
    }

    /** Test-only: see {@link #outageIndicatesBrokenConnection}. */
    boolean outageIndicatesBrokenConnectionForTest() {
        synchronized (lock) {
            return outageIndicatesBrokenConnection;
        }
    }

    /** Test-only: see {@link #recoveredAfterFailedConnectAttempt}. */
    boolean recoveredAfterFailedConnectAttemptForTest() {
        synchronized (lock) {
            return recoveredAfterFailedConnectAttempt;
        }
    }

    /**
     * Test-only accessor (hence the name) over the {@code retired} set. Narrowly named and package-private in
     * preference to widening real API.
     *
     * @param sender the sender to check.
     * @return {@code true} if the sender is retired, i.e. will be closed rather than recycled on release.
     */
    boolean isRetiredForTest(AbstractSender sender) {
        synchronized (lock) {
            return retired.contains(sender);
        }
    }
}
