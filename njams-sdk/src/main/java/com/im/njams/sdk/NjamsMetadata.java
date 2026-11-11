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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.PropertyUtil;
import com.im.njams.sdk.utils.StringUtils;

/**
 * Owns the identifying metadata of an {@link Njams} client: path, category, versions,
 * machine and session id. Obtain via {@code njams.metadata()}.
 */
public class NjamsMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(NjamsMetadata.class);
    /**
     * Key for clientVersion, read from a <code>client.version</code> resource on the classpath. The former
     * spelling <code>clientVersion</code> is still accepted there, but logs a deprecation warning.
     */
    static final String CLIENT_VERSION_KEY = "client.version";
    /**
     * Key for sdkVersion
     */
    static final String SDK_VERSION_KEY = "sdk.version";
    /**
     * Key for build-year. Underscore prefix to avoid printing it in the startup banner.
     */
    private static final String BUILD_YEAR = "_sdk.buildYear";
    /**
     * The only version file a client implementation is meant to ship. The other version files are internal:
     * they are shipped by the SDK and the message format, and never provide the client's version.
     */
    private static final String CLIENT_VERSION_FILE = "client.version";

    private static final String[] VERSION_FILES = { "sdk.version", "msg.version", CLIENT_VERSION_FILE };

    /**
     * Former spelling of {@link NjamsMetadata#CLIENT_VERSION_KEY}, still accepted in version files.
     *
     * @deprecated Has to be removed together with the fallback it backs in
     *             {@link #resolveClientVersion(Properties, String)}.
     */
    @Deprecated(since = "6.1.0", forRemoval = true)
    private static final String DEPRECATED_CLIENT_VERSION_KEY = "clientVersion";

    /** Regex for the simple pattern {@code njams.*.debug.*} (literal dots, {@code *} matches any characters). */
    private static final Pattern DEBUG_SETTING_KEY_PATTERN = Pattern.compile("njams\\..*\\.debug\\..*");
    private static final String UNKNOWN = "unknown";

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
     * Returns the version of the nJAMS client implementation, never <code>null</code> or empty.
     *
     * @return the clientVersion
     */
    public String getClientVersion() {
        return versions.get(CLIENT_VERSION_KEY);
    }

    /**
     * Returns the version of this SDK.
     *
     * @return the sdkVersion
     */
    public String getSdkVersion() {
        return versions.get(SDK_VERSION_KEY);
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
        this.runtimeVersion = runtimeVersion;
        return this;
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
     * Reads all version files found on the classpath into the versions announced to the nJAMS server, and
     * resolves the client version from the client's own {@value #CLIENT_VERSION_FILE} files only.
     *
     * @param version the client version passed to the {@link Njams} constructor
     * @throws NjamsSdkRuntimeException if no client version can be resolved
     */
    private void readVersionsFromVersionFile(String version) {
        final Properties clientVersionProperties = new Properties();
        for (String versionFile : VERSION_FILES) {
            for (URL url : findVersionFiles(versionFile)) {
                LOG.debug("Reading {}", url);
                final Properties prop = new Properties();
                try (InputStream is = url.openStream()) {
                    prop.load(is);
                } catch (Exception e) {
                    LOG.error("Unable to load versions from {}", url, e);
                    continue;
                }
                prop.forEach((k, v) -> versions.put(String.valueOf(k), String.valueOf(v)));
                if (CLIENT_VERSION_FILE.equals(versionFile)) {
                    copyUsableValues(prop, clientVersionProperties);
                }
            }
        }
        final String clientVersion = resolveClientVersion(clientVersionProperties, version);
        versions.remove(DEPRECATED_CLIENT_VERSION_KEY);
        versions.put(CLIENT_VERSION_KEY, clientVersion);
        if (!versions.containsKey(SDK_VERSION_KEY)) {
            LOG.debug("No version file for {} found!", SDK_VERSION_KEY);
            versions.put(SDK_VERSION_KEY, UNKNOWN);
        }
    }

    /**
     * Lists all classpath resources with the given version file name.
     *
     * @param versionFile the resource name to look for
     * @return the resources found, empty if none exists or the lookup failed
     */
    private Collection<URL> findVersionFiles(String versionFile) {
        try {
            return Collections.list(getClass().getClassLoader().getResources(versionFile));
        } catch (IOException e) {
            LOG.error("Unable to list version files: {}", versionFile, e);
            return Collections.emptyList();
        }
    }

    /**
     * Copies only entries that carry a usable value, so that a blank entry cannot shadow a usable one found
     * under the other spelling.
     *
     * @param source the properties read from a version file
     * @param target the properties to add the usable entries to
     */
    private static void copyUsableValues(Properties source, Properties target) {
        source.forEach((k, v) -> {
            final String value = String.valueOf(v);
            if (StringUtils.isNotBlank(value)) {
                target.setProperty(String.valueOf(k), value);
            }
        });
    }

    /**
     * Resolves the client version from the client's own version file, falling back to the version passed to the
     * {@link Njams} constructor. The version file wins over the constructor argument.
     *
     * @param clientVersionProperties the entries read from the {@value #CLIENT_VERSION_FILE} files
     * @param version the client version passed to the {@link Njams} constructor, may be <code>null</code>
     * @return the effective client version, never <code>null</code> or empty
     * @throws NjamsSdkRuntimeException if neither the version file nor the constructor provides a version
     */
    private String resolveClientVersion(Properties clientVersionProperties, String version) {
        final String fromVersionFile = PropertyUtil.getPropertyWithDeprecationWarning(
            clientVersionProperties, CLIENT_VERSION_KEY, DEPRECATED_CLIENT_VERSION_KEY);
        if (StringUtils.isNotBlank(fromVersionFile)) {
            return fromVersionFile;
        }
        if (StringUtils.isNotBlank(version)) {
            return version;
        }
        throw new NjamsSdkRuntimeException("No client version found. Either provide it as property '"
            + CLIENT_VERSION_KEY + "' in a " + CLIENT_VERSION_FILE
            + " file on the classpath, or pass it to the Njams constructor.");
    }

    void printStartupBanner(ClientSettings settings) {
        LOG.info("************************************************************");
        LOG.info("***      nJAMS SDK: Copyright (c) {} Salesfive Integration Services GmbH",
            versions.get(BUILD_YEAR));
        LOG.info("*** ");
        LOG.info("***      Version Info:");
        versions.entrySet().stream().filter(e -> !e.getKey().startsWith("_"))
            .sorted(Comparator.comparing(Entry::getKey))
            .forEach(e -> LOG.info("***      {}: {}", e.getKey(), e.getValue()));
        LOG.info("*** ");
        LOG.info("***      Settings:");

        settings.printPropertiesWithoutPasswords(LOG);
        if (settings.keySet().stream().anyMatch(DEBUG_SETTING_KEY_PATTERN.asMatchPredicate())) {
            LOG.warn("***      Debug settings (njams.*.debug.*) are enabled. These are intended for "
                + "troubleshooting only and must not be used in production.");
        }
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
            machine = UNKNOWN;
        }
    }
}
