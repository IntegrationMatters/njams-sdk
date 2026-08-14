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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the connection, shutdown, and "was ever connected" / reconnect-gating state for one shared-transport
 * group of senders (all senders handed out by a single {@link SenderPool}), or independently for one receiver
 * group. Replaces the former JVM-global {@code static} reconnect state on {@link AbstractSender} and
 * {@link AbstractReceiver} so that unrelated {@link com.im.njams.sdk.Njams} instances no longer share connection
 * state. Each sender group and each receiver owns its own instance; the two sides never share state.
 * Internal SDK infrastructure — not public API.
 */
class ConnectionCoordinator {

    private final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);
    private volatile boolean wasEverConnected = false;
    private volatile boolean reconnectBeforeConnected = false;

    /**
     * Marks the group as currently disconnected and beginning a reconnect.
     *
     * @return {@code true} if this call cleared the connected flag, {@code false} if the group was already
     *         marked disconnected.
     */
    synchronized boolean beginReconnect() {
        return hasConnected.compareAndSet(true, false);
    }

    /**
     * Records a successful (re)connect.
     *
     * @return {@code true} exactly once for the disconnected&rarr;connected transition (so the caller can log the
     *         reconnect a single time), {@code false} if the group was already marked connected.
     */
    synchronized boolean markConnected() {
        wasEverConnected = true;
        return hasConnected.compareAndSet(false, true);
    }

    /** @return {@code true} while the group is currently believed to be connected. */
    boolean isGroupConnected() {
        return hasConnected.get();
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
