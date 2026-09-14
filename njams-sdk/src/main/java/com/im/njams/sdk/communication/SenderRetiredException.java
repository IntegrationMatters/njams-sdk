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

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Internal signal: the sender a send was running on was retired by its pool before that send finished, so the
 * failure it ended on says nothing about the group's current connection.
 * <p>
 * Deliberately package-private — no transport ever throws or catches this; it travels from
 * {@link AbstractSender#sendWithRetry(SendAttempt)} to {@link NjamsSender}, which retries the message on a fresh
 * sender instead of reporting an outage. It extends {@link NjamsSdkRuntimeException} so a transport's own
 * {@code send} wrapping cannot turn it into a checked exception.
 */
class SenderRetiredException extends NjamsSdkRuntimeException {

    private static final long serialVersionUID = 1L;

    /** Guards against a pathological transport exception whose cause chain loops back on itself. */
    private static final int MAX_CAUSE_DEPTH = 32;

    SenderRetiredException(Throwable cause) {
        super("Sender was retired while the message was still being retried", cause);
    }

    /**
     * Detects the signal anywhere in a failure's cause chain. A chain walk rather than an {@code instanceof} on
     * purpose: {@code JmsSender.send} wraps every failure unconditionally, so the signal does not arrive at the
     * top level on every transport.
     *
     * @param failure the failure to inspect; may be {@code null}.
     * @return {@code true} if this signal is the failure or one of its causes.
     */
    static boolean isIn(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SenderRetiredException) {
                return true;
            }
            final Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }
}
