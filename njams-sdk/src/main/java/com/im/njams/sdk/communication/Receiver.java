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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * This interface must be implenmented by a new Receiver for a given
 * communication
 *
 * @author pnientiedt
 */
public interface Receiver {

    /**
     * Set njams instance
     *
     * @param njams instance
     */
    void setNjams(Njams njams);

    /**
     * The implementation should return its name here, by which it can be
     * identified. This name will be used as value in the
     * CommunicationConfiguration via the Key
     * {@value NjamsSettings#PROPERTY_COMMUNICATION} (or its alternative
     * {@value NjamsSettings#PROPERTY_COMMUNICATION_TYPE})
     *
     * @return the name of the receiver implementation
     */
    String getName();

    /**
     * Initializes the receiver with the given settings.
     *
     * @param settings the settings to be used for initialization
     */
    void init(ClientSettings settings);

    /**
     * This function should be called by a implementation of the Receiver class
     * with the previously read instruction
     *
     * @param instruction will be executed for all listeners
     */
    void onInstruction(Instruction instruction);

    /**
     * Start the new Receiver
     */
    void start();

    /**
     * Starts this receiver for the initial connection, waiting at most {@code timeoutMs} milliseconds
     * for the connection to be established.
     * <p>
     * Unlike {@link #start()}, implementations must <strong>not</strong> trigger the reconnect
     * mechanism on failure — if the connection cannot be established within the given time,
     * this method must throw and leave the receiver inactive.
     * <p>
     * The default implementation ignores the timeout and delegates to {@link #start()}.
     * {@link AbstractReceiver} overrides this with a proper timeout-enforced implementation that
     * also supports early connection start via {@link AbstractReceiver#beginConnect()}.
     *
     * @param timeoutMs maximum time in milliseconds to wait for the connection
     * @throws NjamsSdkRuntimeException if the connection cannot be
     *         established within {@code timeoutMs} or an error occurs during connection
     * @since 6.0.0
     */
    default void startWithTimeout(long timeoutMs) {
        start();
    }

    /**
     * Starts this receiver for the initial connection like {@link #startWithTimeout(long)}, but instead of
     * throwing on failure, applies the given policy: if {@code reconnectOnFailure} is {@code true} the
     * implementation may enter a background reconnect and report success anyway; otherwise this behaves like
     * {@link #startWithTimeout(long)} except it reports failure via its return value instead of throwing.
     * <p>
     * The default implementation has no reconnect mechanism to fall back on (that requires {@link
     * AbstractReceiver}), so it ignores {@code reconnectOnFailure} and simply reports the outcome of one attempt.
     * {@link AbstractReceiver} overrides this with a coordinator-aware implementation that honors {@code
     * reconnectOnFailure}.
     *
     * @param timeoutMs maximum time in milliseconds to wait for the connection.
     * @param reconnectOnFailure whether a failed initial connect should be retried in the background instead of
     *        failing.
     * @return {@code true} if the SDK may proceed (connected, or reconnecting in the background); {@code false} to
     *         fail startup.
     * @since 6.0.0
     */
    default boolean startWithTimeout(long timeoutMs, boolean reconnectOnFailure) {
        try {
            startWithTimeout(timeoutMs);
            return true;
        } catch (NjamsSdkRuntimeException e) {
            return false;
        }
    }

    /**
     * Stop the new Receiver
     */
    void stop();

}
