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
package com.im.njams.sdk.communication.jms;

import org.slf4j.Logger;

import com.im.njams.sdk.communication.TransportNotApplicableException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Resolves the configured {@link JmsFactory}, which is the probe that tells whether it is a {@code jakarta.jms}
 * implementation. Shared by this tree's sender and receiver; the {@code javax} tree has its own copy, because
 * {@link JmsFactory} is namespace-bound and cannot be named once for both.
 */
final class JmsFactoryProbe {

    private JmsFactoryProbe() {
        // static access only
    }

    /**
     * Resolves and initializes the configured factory, then closes it again.
     *
     * @param settings the settings the sender or receiver is initialized with
     * @param log the caller's logger, so the resolution is reported under the calling class
     * @throws TransportNotApplicableException if the configured factory is not a {@code jakarta.jms} implementation
     */
    static void probe(ClientSettings settings, Logger log) {
        JmsFactory probe = null;
        try {
            probe = JmsFactory.find(settings, false);
            try (JmsFactory jmsFactory = probe) {
                jmsFactory.init(settings);
                log.debug("Resolved JMS factory {}", jmsFactory);
            }
        } catch (Throwable e) {
            if (TransportNotApplicableException.isNotApplicable(e)) {
                throw new TransportNotApplicableException(
                    "The configured JMS factory is not a jakarta.jms implementation.", e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            if (e instanceof Error) {
                throw (Error) e;
            }
            throw new IllegalStateException("Could not initialize: " + probe, e);
        }
    }
}
