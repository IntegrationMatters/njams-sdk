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

/**
 * Outcome of {@link Njams#startup()}, telling the client how it is expected to react. The SDK itself treats
 * {@link #FAIL} and {@link #EXIT} identically: in both cases the instance stays inactive. The difference only exists
 * for clients that want to honor it, and it is up to each client to implement it; a client that does not
 * differentiate simply treats both as a failed startup. See the respective client's documentation for how it
 * reacts.
 *
 * @since 6.1.0
 */
public enum StartupResult {
    /** The SDK instance started; the client continues its normal startup. */
    SUCCESS,
    /**
     * Startup failed and the SDK instance is inactive. The client is expected to let the runtime continue without
     * it, i.e., to withdraw itself from the runtime as far as possible.
     */
    FAIL,
    /**
     * Startup failed and the SDK instance is inactive; the configured startup fail-behavior is {@code exit}. The
     * client is expected to terminate the runtime.
     */
    EXIT
}
