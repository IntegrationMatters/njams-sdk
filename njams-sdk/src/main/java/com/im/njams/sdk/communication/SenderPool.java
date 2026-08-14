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
 * thread. When the connector has a working sender it publishes it via {@link #onReconnected(AbstractSender)},
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

    private boolean reconnecting = false;
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
     * Creates a new, not-yet-connected sender.
     *
     * @return the new sender, or {@code null} once {@link #declareShutdown()} has been called.
     */
    protected AbstractSender create() {
        if (shutdown) {
            return null;
        }
        final AbstractSender sender = factory.getSender();
        sender.setConnectionCoordinator(coordinator);
        sender.setFailureSink(this::reportFailure);
        return sender;
    }

    /**
     * Kept for the legacy {@link #get()} path.
     *
     * @param sender the sender to validate
     * @return always {@code true}
     */
    public boolean validate(AbstractSender sender) {
        // TODO: there must be a better solution!
        return true;
    }

    /**
     * Closes one sender and drops it. Never call this while holding {@link #lock}: a real transport's
     * {@code close()} can block, which would stall the whole group's acquire/release traffic.
     * <p>
     * Deliberately does <em>not</em> set the sender's shutdown flag. That flag lives on the shared
     * {@link ConnectionCoordinator}, so retiring a single broken sender would otherwise abort the group's elected
     * reconnect. Group shutdown is set explicitly by {@link #beginShutdown()}, {@link #declareShutdown()} and
     * {@link #shutdown()}.
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
                final AbstractSender created = create();
                if (created == null) {
                    // Shutdown was declared while we were here.
                    return null;
                }
                try {
                    // B2 (confirmed): the connect deliberately happens inside the monitor. Reserving a slot and
                    // connecting outside the lock was considered and declined — it reintroduces the parallel-
                    // connect storm this design removes. The cost is bounded: at most maxSenderThreads callers
                    // ever reach here, and only while the group is healthy, so the connect is expected to be
                    // fast. A partially degraded endpoint (accepting some connects, hanging others) can still
                    // hold the lock for the duration of one connect; that is a known, accepted residual risk,
                    // not a bug to fix here. Do not "optimize" this out.
                    created.connect();
                    locked.add(created);
                    LOG.debug("Created and connected sender: {}", created);
                    return created;
                } catch (Exception e) {
                    // The group is broken, not just this attempt: fail it inline rather than re-entering
                    // reportFailure(), which would deadlock on the lock we already hold.
                    halfBuilt = created;
                    connectFailure = e;
                    if (!reconnecting) {
                        toDestroy = failGroup();
                        toNotify = new ArrayList<>(exceptionListeners);
                    }
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
        List<AbstractSender> toDestroy = Collections.emptyList();
        List<SenderExceptionListener> listeners = null;
        synchronized (lock) {
            locked.remove(sender);
            retired.remove(sender);
            if (!reconnecting) {
                toDestroy = failGroup();
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
     */
    void onReconnected(AbstractSender connected) {
        synchronized (lock) {
            reconnecting = false;
            failed = false;
            lastPublished = connected;
            unlocked.add(connected);
            lock.notifyAll();
        }
    }

    /**
     * Flips the group into the failed/reconnecting state and retires everything currently checked out. Must be
     * called with {@link #lock} held and only while {@code !reconnecting}.
     *
     * @return the idle senders the caller must close <em>after</em> releasing the lock.
     */
    private List<AbstractSender> failGroup() {
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
     * Legacy borrow method: hands out an idle or newly created sender without connecting it. Superseded by
     * {@link #acquire()}, which only ever hands out a connected sender and honours the group's failure state.
     *
     * @return an idle or newly created sender, or {@code null} once {@link #declareShutdown()} has been called.
     */
    public AbstractSender get() {
        final List<AbstractSender> invalid = new ArrayList<>(0);
        final AbstractSender result;
        synchronized (lock) {
            LOG.trace("Get locked={}, unlocked={}", locked.size(), unlocked.size());
            AbstractSender reused = null;
            // hand out an existing, still valid sender if one is available
            final Iterator<AbstractSender> it = unlocked.iterator();
            while (it.hasNext()) {
                final AbstractSender sender = it.next();
                it.remove();
                if (validate(sender)) {
                    locked.add(sender);
                    reused = sender;
                    break;
                }
                // object failed validation
                LOG.debug("Sender {} failed validation!", sender);
                invalid.add(sender);
            }
            if (reused != null) {
                LOG.trace("Got sender: {}", reused);
                result = reused;
            } else {
                // no objects available, create a new one
                LOG.trace("Creating new sender, locked={}, unlocked={}", locked.size(), unlocked.size());
                result = create();
                if (result != null) {
                    if (shutdown) {
                        result.setShouldShutdown(true);
                    }
                    locked.add(result);
                }
                LOG.debug("Created sender: {} (shouldShutdown={})", result, shutdown);
            }
        }
        invalid.forEach(this::destroy);
        return result;
    }

    /**
     * Legacy return method: puts the sender back into the pool unconditionally. Superseded by
     * {@link #release(AbstractSender)}, which also honours retirement and shutdown.
     *
     * @param t the sender to return to the pool.
     */
    public void close(AbstractSender t) {
        synchronized (lock) {
            locked.remove(t);
            unlocked.add(t);
            LOG.trace("Close locked={}, unlocked={}", locked.size(), unlocked.size());
        }
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
        final List<AbstractSender> all;
        synchronized (lock) {
            // Set before cancelling, so a waiter woken by a reconnect thread that is finishing concurrently
            // already sees the drain instead of picking the group back up.
            draining = true;
            all = allSenders();
            lock.notifyAll();
        }
        connector.cancelReconnect();
        all.forEach(AbstractSender::cancelReconnect);
    }

    /**
     * Completes shutdown: from here on no new sender is created or handed out, and every sender still checked out
     * is closed when its borrower releases it.
     */
    public void declareShutdown() {
        final List<AbstractSender> all;
        synchronized (lock) {
            shutdown = true;
            draining = true;
            all = allSenders();
            lock.notifyAll();
        }
        coordinator.setShouldShutdown(true);
        all.forEach(s -> s.setShouldShutdown(true));
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
     * {@link #onReconnected(AbstractSender)} and returns it. Uses the pool's own lock/wait pair rather than
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
