/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.njams.metadata;

import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

class NjamsVersions {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsVersions.class);

    static final String CLIENT_VERSION_KEY = "clientVersion";

    static final String SDK_VERSION_KEY = "sdkVersion";

    private final Map<String, String> versions;

    NjamsVersions(Map<String, String> versions) {
        this.versions = versions;
    }

    NjamsVersions setSdkVersionIfAbsent(String sdkDefaultVersion) {
        return setDefaultVersionFor(sdkDefaultVersion, SDK_VERSION_KEY);
    }

    NjamsVersions setClientVersionIfAbsent(String clientDefaultVersion) {
        return setDefaultVersionFor(clientDefaultVersion, CLIENT_VERSION_KEY);
    }

    private NjamsVersions setDefaultVersionFor(String clientDefaultVersion, String key) {
        if (!versions.containsKey(key)) {
            LOG.debug("No njams.version file for {} found!", key);
            versions.put(key, clientDefaultVersion);
        }
        return this;
    }

    String getClientVersion() {
        return versions.get(CLIENT_VERSION_KEY);
    }

    String getSdkVersion() {
        return versions.get(SDK_VERSION_KEY);
    }

    Map<String, String> getAllVersions() {
        return Collections.unmodifiableMap(versions);
    }
}
