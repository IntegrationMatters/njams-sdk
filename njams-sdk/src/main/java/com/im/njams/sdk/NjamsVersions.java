package com.im.njams.sdk;

import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class NjamsVersions {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NjamsVersions.class);

    /**
     * Key for clientVersion
     */
    private static final String CLIENT_VERSION_KEY = "clientVersion";
    /**
     * Key for sdkVersion
     */
    private static final String SDK_VERSION_KEY = "sdkVersion";

    private final Map<String, String> versions;

    public NjamsVersions(Map<String, String> versions) {
        this.versions = versions;
    }

    public NjamsVersions setSdkVersionIfAbsent(String sdkDefaultVersion) {
        return setDefaultVersionFor(sdkDefaultVersion, SDK_VERSION_KEY);
    }

    public NjamsVersions setClientVersionIfAbsent(String clientDefaultVersion) {
        return setDefaultVersionFor(clientDefaultVersion, CLIENT_VERSION_KEY);
    }

    private NjamsVersions setDefaultVersionFor(String clientDefaultVersion, String key) {
        if (!versions.containsKey(key)) {
            LOG.debug("No njams.version file for {} found!", key);
            versions.put(key, clientDefaultVersion);
        }
        return this;
    }

    public String getClientVersion() {
        return versions.get(CLIENT_VERSION_KEY);
    }

    public String getSdkVersion() {
        return versions.get(SDK_VERSION_KEY);
    }

    public Map<String, String> getAllVersions() {
        return Collections.unmodifiableMap(versions);
    }
}
