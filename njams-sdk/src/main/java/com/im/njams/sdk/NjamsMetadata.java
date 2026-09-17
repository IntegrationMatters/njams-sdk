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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;

/**
 * Owns the identifying metadata of an {@link Njams} client: path, category, versions,
 * machine and session id. Obtain via {@code njams.metadata()}.
 */
public class NjamsMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsMetadata.class);

    private static final String[] VERSION_FILES = { "njams.version", "msg.version", "client.version" };

    private final String category;
    private final Path clientPath;
    private final String clientSessionId;
    private final LocalDateTime startTime;
    private final Map<String, String> versions = new HashMap<>();

    private String machine;
    private String runtimeVersion;

    private final LifecycleState lifecycle;

    NjamsMetadata(Path clientPath, String version, String category, LifecycleState lifecycle) {
        this.clientPath = clientPath;
        this.category = category == null ? null : category.toUpperCase();
        this.lifecycle = lifecycle;
        startTime = DateTimeUtility.now();
        clientSessionId = UUID.randomUUID().toString();
        readVersionsFromVersionFile(version);
        setMachine();
    }

    /**
     * Returns the category of the nJAMS client, which should describe the technology.
     *
     * @return the category of the nJAMS client
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the path of this client in the object tree.
     *
     * @return the clientPath
     */
    public Path getClientPath() {
        return clientPath;
    }

    /**
     * Returns a transient UUID that identifies this {@link Njams} client instance during its JVM lifetime.
     * This is internally used for (container-mode) communications.
     *
     * @return The current ID of this client.
     */
    public String getClientSessionId() {
        return clientSessionId;
    }

    /**
     * Returns the version of the nJAMS client implementation.
     *
     * @return the clientVersion
     */
    public String getClientVersion() {
        return versions.get(Njams.CLIENT_VERSION_KEY);
    }

    /**
     * Returns the version of this SDK.
     *
     * @return the sdkVersion
     */
    public String getSdkVersion() {
        return versions.get(Njams.SDK_VERSION_KEY);
    }

    /**
     * Returns the version of the underlying runtime (eg. Mule, BW6, ...), if set.
     *
     * @return the runtimeVersion, or <code>null</code> if none was set
     */
    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    /**
     * Sets the version of the underlying runtime (eg. Mule, BW6, ...). The runtime version is
     * announced to the nJAMS server in the project message when the client starts.
     *
     * @param runtimeVersion the runtime version to set
     * @return this facet, for call chaining
     * @throws NjamsSdkRuntimeException if the client has already been started — a later change
     *                                  would never reach the server
     */
    public NjamsMetadata setRuntimeVersion(String runtimeVersion) {
        lifecycle.requireNotStarted("NjamsMetadata.setRuntimeVersion");
        setRuntimeVersionInternal(runtimeVersion);
        return this;
    }

    void setRuntimeVersionInternal(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * Returns the name of the machine this client runs on.
     *
     * @return the machine name
     */
    public String getMachine() {
        return machine;
    }

    /** The start time of the engine; sent in the project message. */
    LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Read the versions from njams.version files. Set the SDK-Version and the
     * Client-Version if found.
     *
     * @param version
     */
    private void readVersionsFromVersionFile(String version) {
        final Collection<URL> urls =
            Arrays.stream(VERSION_FILES)
                .map(v -> {
                    try {
                        return Collections.list(getClass().getClassLoader().getResources(v));
                    } catch (IOException e) {
                        LOG.error("Unable to list version files: {}", v, e);
                        return null;
                    }
                }).filter(Objects::nonNull)
                .flatMap(Collection::stream).collect(Collectors.toList());
        for (URL url : urls) {
            LOG.debug("Reading {}", url);
            final Properties prop = new Properties();
            try (InputStream is = url.openStream()) {
                prop.load(is);
                prop.entrySet()
                    .forEach(e -> versions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
            } catch (Exception e) {
                LOG.error("Unable to load versions from {}", url, e);
            }
        }
        if (version != null && !versions.containsKey(Njams.CLIENT_VERSION_KEY)) {
            LOG.debug("No version file for {} found!", Njams.CLIENT_VERSION_KEY);
            versions.put(Njams.CLIENT_VERSION_KEY, version);
        }
        if (!versions.containsKey(Njams.SDK_VERSION_KEY)) {
            LOG.debug("No version file for {} found!", Njams.SDK_VERSION_KEY);
            versions.put(Njams.SDK_VERSION_KEY, "5.0.0.dev");
        }
    }

    void printStartupBanner(ClientSettings settings) {
        LOG.info("************************************************************");
        LOG.info(
            "***      nJAMS SDK: Copyright (c) " + versions.get(Njams.BUILD_YEAR)
                + " Salesfive Integration Services GmbH");
        LOG.info("*** ");
        LOG.info("***      Version Info:");
        versions.entrySet().stream().filter(e -> !e.getKey().toLowerCase().contains("buildyear"))
            .sorted(Comparator.comparing(Entry::getKey))
            .forEach(e -> LOG.info("***      {}: {}", e.getKey(), e.getValue()));
        LOG.info("*** ");
        LOG.info("***      Settings:");

        settings.printPropertiesWithoutPasswords(LOG);
        LOG.info("************************************************************");

    }

    /**
     * Set the machine name
     */
    private void setMachine() {
        try {
            machine = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LOG.debug("Error getting machine name", e);
            machine = "unknown";
        }
    }
}
