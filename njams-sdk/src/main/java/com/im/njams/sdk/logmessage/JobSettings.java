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
package com.im.njams.sdk.logmessage;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Immutable per-client snapshot of the job-related settings (error logging, truncation,
 * payload limits). Parsed once per {@link ClientSettings} instance instead of for every job.
 */
final class JobSettings {

    // one entry per Njams instance; instances are few and long-lived, no eviction needed
    private static final ConcurrentMap<ClientSettings, JobSettings> CACHE = new ConcurrentHashMap<>();

    final boolean allErrors;
    final boolean truncateOnSuccess;
    final int truncateLimit;
    /** null = no payload limit; key true = truncate, false = discard; value = limit */
    final Entry<Boolean, Integer> payloadLimit;

    static JobSettings of(ClientSettings settings) {
        return CACHE.computeIfAbsent(settings, JobSettings::new);
    }

    private JobSettings(ClientSettings settings) {
        allErrors = settings.getBool(NjamsSettings.PROPERTY_LOG_ALL_ERRORS, false);
        truncateOnSuccess = settings.getBool(NjamsSettings.PROPERTY_TRUNCATE_ON_SUCCESS, false);
        final int limit = settings.getInt(NjamsSettings.PROPERTY_TRUNCATE_LIMIT, Integer.MAX_VALUE);
        truncateLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        payloadLimit = parsePayloadLimit(settings);
    }

    private static Entry<Boolean, Integer> parsePayloadLimit(ClientSettings settings) {
        // truncate, discard
        final String mode = settings.getProperty(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_MODE);
        if (StringUtils.isBlank(mode)) {
            return null;
        }
        final int limit = settings.getInt(NjamsSettings.PROPERTY_PAYLOAD_LIMIT_SIZE, -1);
        if (limit < 0) {
            return null;
        }
        if (limit == 0 || "discard".equalsIgnoreCase(mode)) {
            return new AbstractMap.SimpleImmutableEntry<>(false, limit);
        }
        if ("truncate".equalsIgnoreCase(mode)) {
            return new AbstractMap.SimpleImmutableEntry<>(true, limit);
        }
        return null;
    }
}
