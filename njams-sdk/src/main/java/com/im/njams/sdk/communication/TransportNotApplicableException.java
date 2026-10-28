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

/**
 * Thrown by a transport implementation from its initialization to state that it is not applicable in the current
 * environment, e.g. because a library it requires is not present. This is an expected outcome rather than an
 * error: the SDK skips such an implementation and looks for another one.
 */
public class TransportNotApplicableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message Describes why the transport is not applicable.
     */
    public TransportNotApplicableException(final String message) {
        super(message);
    }

    /**
     * @param message Describes why the transport is not applicable.
     * @param cause The underlying reason.
     */
    public TransportNotApplicableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
