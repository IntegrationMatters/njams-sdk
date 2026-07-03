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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the connection lifecycle state for one shared-transport group of senders (all senders handed out by a
 * single {@link SenderPool}). Replaces the former JVM-global {@code static} reconnect state on
 * {@link AbstractSender} so that unrelated {@link com.im.njams.sdk.Njams} instances no longer share connection
 * state. Internal SDK infrastructure — not public API.
 */
class ConnectionCoordinator {

    private final AtomicBoolean hasConnected = new AtomicBoolean(false);
    private final AtomicInteger connecting = new AtomicInteger(0);
    private volatile boolean connectionFailure = false;
    private final AtomicBoolean shouldShutdown = new AtomicBoolean(false);

    /**
     * Marks the group as currently disconnected and beginning a reconnect: clears the connected flag, raises the
     * connection-failure flag, and increments the reconnecting count.
     *
     * @return the reconnecting count after incrementing (for logging).
     */
    synchronized int beginReconnect() {
        hasConnected.set(false);
        connectionFailure = true;
        return connecting.incrementAndGet();
    }

    /**
     * Records a successful (re)connect: clears the connection-failure flag and decrements the reconnecting count.
     *
     * @return {@code true} exactly once for the disconnected&rarr;connected transition (so the caller can log the
     *         reconnect a single time), {@code false} if the group was already marked connected.
     */
    synchronized boolean markConnected() {
        connectionFailure = false;
        connecting.decrementAndGet();
        return hasConnected.compareAndSet(false, true);
    }

    /** @return the current number of in-progress reconnects in this group. */
    synchronized int reconnectingCount() {
        return connecting.get();
    }

    /** @return {@code true} while the group is in a connection-failure state. */
    boolean isConnectionFailure() {
        return connectionFailure;
    }

    /** Sets the connection-failure flag for the group. */
    void setConnectionFailure(boolean value) {
        connectionFailure = value;
    }

    /** @return {@code true} once the group is shutting down. */
    boolean shouldShutdown() {
        return shouldShutdown.get();
    }

    /** Sets the shutdown flag for the group. */
    void setShouldShutdown(boolean value) {
        shouldShutdown.set(value);
    }
}
