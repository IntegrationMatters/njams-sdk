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

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the reconnect-counting, shutdown, and "was ever connected" / reconnect-gating state for one shared-transport
 * group of senders (all senders handed out by a single {@link SenderPool}), or independently for one receiver
 * group. Replaces the former JVM-global {@code static} reconnect state on {@link AbstractSender} and
 * {@link AbstractReceiver} so that unrelated {@link com.im.njams.sdk.Njams} instances no longer share connection
 * state. Also carries optional cross-side triggers (see {@link #addCrossSideTrigger(Runnable)}) so a failure on
 * one side of a wired sender/receiver pair can prompt the other (or others, for a shared sender pool with
 * multiple per-instance receivers) to verify its own connection, without the two sides sharing any other state.
 * Internal SDK infrastructure — not public API.
 */
class ConnectionCoordinator {

    private final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private final AtomicInteger connecting = new AtomicInteger(0);
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);
    private volatile boolean wasEverConnected = false;
    private volatile boolean reconnectBeforeConnected = false;
    private final Collection<Runnable> crossSideTriggers = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Marks the group as currently disconnected and beginning a reconnect: clears the connected flag and
     * increments the reconnecting count. Fires every registered cross-side trigger exactly once — on the
     * transition into the first concurrently in-progress reconnect for this group, not on every call.
     *
     * @return the reconnecting count after incrementing (for logging).
     */
    synchronized int beginReconnect() {
        hasConnected.set(false);
        int result = connecting.incrementAndGet();
        if (result == 1) {
            crossSideTriggers.forEach(Runnable::run);
        }
        return result;
    }

    /**
     * Registers a callback invoked once per newly detected failure for this group (see {@link #beginReconnect()}).
     * Used to implement the cross-side connection-verification trigger between a sender group and its wired
     * receiver(s): when this side detects a failure, every registered side is prompted to proactively cycle its
     * own connection too, since a mostly-idle side could otherwise be slow to notice a real loss on its own.
     * Add-only — there is deliberately no corresponding removal, mirroring
     * {@link SenderPool#addSenderExceptionListener(SenderExceptionListener)}'s existing shape. Multiple triggers
     * may be registered: with {@code njams.sdk.communication.shared=true} on HTTP, one shared sender pool's
     * coordinator may need to notify several different per-instance receivers, not just the most recently wired
     * one.
     *
     * @param trigger the callback to add.
     */
    synchronized void addCrossSideTrigger(Runnable trigger) {
        crossSideTriggers.add(trigger);
    }

    /**
     * Records a successful (re)connect: decrements the reconnecting count.
     *
     * @return {@code true} exactly once for the disconnected&rarr;connected transition (so the caller can log the
     *         reconnect a single time), {@code false} if the group was already marked connected.
     */
    synchronized boolean markConnected() {
        connecting.decrementAndGet();
        wasEverConnected = true;
        return hasConnected.compareAndSet(false, true);
    }

    /** @return the current number of in-progress reconnects in this group. */
    synchronized int reconnectingCount() {
        return connecting.get();
    }

    /** @return {@code true} once the group is shutting down. */
    boolean shouldShutdown() {
        return shouldShutdown.get();
    }

    /** Sets the shutdown flag for the group. */
    void setShouldShutdown(boolean value) {
        shouldShutdown.set(value);
    }

    /**
     * Records a successful startup connect (see {@link AbstractSender#beginConnect()}): marks the group connected
     * and, stickily, that it has connected at least once. Unlike {@link #markConnected()} this does not touch the
     * reconnect counter, because a startup connect is not preceded by {@link #beginReconnect()}.
     *
     * @return {@code true} on the disconnected&rarr;connected transition, {@code false} if already connected.
     */
    synchronized boolean markStartupConnected() {
        wasEverConnected = true;
        return hasConnected.compareAndSet(false, true);
    }

    /** @return {@code true} once any connect (startup or reconnect) has succeeded for this group. */
    boolean wasEverConnected() {
        return wasEverConnected;
    }

    /**
     * Permits reconnect attempts even before the first successful connect. Set when the startup fail-behavior is
     * {@code reconnect} so a failed initial connect enters the background reconnect loop instead of failing fast.
     */
    void allowReconnectBeforeConnected() {
        reconnectBeforeConnected = true;
    }

    /**
     * @return {@code true} if a reconnect may proceed now: the group is not shutting down and it has either
     *         connected before (Phase 2) or been told to reconnect from startup (Phase 1 {@code reconnect} policy).
     */
    boolean shouldReconnect() {
        return !shouldShutdown() && (wasEverConnected || reconnectBeforeConnected);
    }
}
