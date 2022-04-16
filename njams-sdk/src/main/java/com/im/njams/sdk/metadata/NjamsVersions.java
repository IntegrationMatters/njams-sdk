package com.im.njams.sdk.metadata;

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
