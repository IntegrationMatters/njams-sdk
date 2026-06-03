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
package com.im.njams.sdk.inject;

/**
 * Names of the nJAMS correlation headers used for header injection (auto-correlation).
 * <p>
 * When one instrumented execution triggers another (for example via an HTTP or JMS call), these
 * headers can be injected into the outbound request and read on the receiving side so that the
 * separate executions are automatically correlated into a single call chain.
 * <p>
 * The SDK only provides these names as constants; injecting the headers into outbound calls and
 * extracting them from inbound calls is the responsibility of the integrating transport or client
 * code.
 *
 * @since 6.0.0
 */
public final class CorrelationHeaders {

    /**
     * Header carrying the correlation log ID, which identifies the root execution in the call chain.
     */
    public static final String NJAMS_CORRELATION_LOG_ID = "NJAMS_CorrelationLogID";

    /**
     * Header carrying the parent log ID, which identifies the immediate calling execution.
     */
    public static final String NJAMS_PARENT_LOG_ID = "NJAMS_ParentLogID";

    /**
     * Header carrying the deep-trace flag (a boolean) that enables verbose tracing throughout the
     * call chain.
     */
    public static final String NJAMS_DEEP_TRACE = "NJAMS_DeepTrace";

    private CorrelationHeaders() {
        // Constants holder; not instantiable.
    }
}
