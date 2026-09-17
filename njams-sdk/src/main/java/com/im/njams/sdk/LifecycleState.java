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
package com.im.njams.sdk;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Holds the started-state of an {@link Njams} instance and provides the uniform
 * phase-guard checks shared by all facets.
 */
final class LifecycleState {

    static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private volatile boolean started = false;

    boolean isStarted() {
        return started;
    }

    void setStarted(boolean started) {
        this.started = started;
    }

    /** Throws if the instance has not been started yet (message identical to current behavior). */
    void requireStarted() {
        if (!started) {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
    }

    /**
     * Throws if the instance has already been started. Used by design-time facet operations
     * whose data is announced to the nJAMS server at start and cannot be changed afterwards.
     *
     * @param operation description used in the error message, e.g. "NjamsFeatures.add"
     */
    void requireNotStarted(String operation) {
        if (started) {
            throw new NjamsSdkRuntimeException(
                operation + " is not allowed after start(): this information is announced to the nJAMS server"
                    + " when the client starts and cannot be changed afterwards.");
        }
    }
}
