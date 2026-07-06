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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Startup fail-behavior for the transport connection, parsed from
 * {@link NjamsSettings#PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR}. Internal SDK infrastructure — not public API.
 */
enum StartupFailBehavior {
    /** Initial connect failure fails startup: {@code Njams.start()} returns {@code false}, SDK inactive. */
    FAIL,
    /** Initial connect failure enters the background reconnect loop; {@code Njams.start()} returns {@code true}. */
    RECONNECT;

    /** Default when the setting is absent or unrecognised. */
    static final StartupFailBehavior DEFAULT = FAIL;

    private static final Logger LOG = LoggerFactory.getLogger(StartupFailBehavior.class);

    static StartupFailBehavior fromSettings(ClientSettings settings) {
        String value = settings.getProperty(NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }
        String normalized = value.trim();
        if (RECONNECT.name().equalsIgnoreCase(normalized)) {
            return RECONNECT;
        }
        if (FAIL.name().equalsIgnoreCase(normalized)) {
            return FAIL;
        }
        LOG.warn("Unknown value '{}' for {}; defaulting to '{}'.", value,
            NjamsSettings.PROPERTY_COMMUNICATION_STARTUP_FAILBEHAVIOR, DEFAULT.name().toLowerCase());
        return DEFAULT;
    }

    boolean reconnectOnStartupFailure() {
        return this == RECONNECT;
    }
}
