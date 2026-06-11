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

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.client.LogMessageFlushTask;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.*;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.provider.FileConfigurationProvider;
import com.im.njams.sdk.logmessage.DataMasking;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.CommonBfsModelLayouter;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.ClientSettings;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 *
 * @author bwand
 */
public class Njams implements InstructionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);

    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 30_000L;

    /**
     * Defines the standard set of optional features that an nJAMS client may support.
     */
    public enum Feature {
        /**
         * Value indicating that this instance supports replay functionality.
         */
        REPLAY("replay"),
        /**
         * Value indicating that this instance supports the header injection feature.
         */
        INJECTION("injection"),
        /**
         * Value indicating that this instance implements expression test functionality.
         */
        EXPRESSION_TEST("expressionTest"),
        /**
         * Value indicating that this instance implements replying to a "ping" request sent from nJAMS server.
         */
        PING("ping"),
        /**
         * Value indicating that this instance supports the container mode feature with unique client ids.
         */
        CONTAINER_MODE("containerMode"),
        /**
         * Whether the client supports processing fragmented commands.
         */
        COMMANDS_SPLIT("commandSplit");

        /**
         * Inherent features implemented in this SDK that are always active.
         */
        static final Collection<Feature> INHERENT_FEATURES = Collections.unmodifiableCollection(
            Arrays.asList(Feature.EXPRESSION_TEST, Feature.PING, Feature.COMMANDS_SPLIT));

        private final String key;

        private Feature(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }

        /**
         * Raw string value to be used when sending information to nJAMS.
         *
         * @return Raw string value.
         */
        public String key() {
            return key;
        }

        /**
         * Tries to find the instance according to the given name.
         *
         * @param name The name of the instance that shall be returned.
         * @return The instance for the given name, or <code>null</code> if no matching instance was found.
         */
        public static Feature byName(String name) {
            for (Feature f : values()) {
                if (f.name().equalsIgnoreCase(name) || f.key.equalsIgnoreCase(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    /**
     * Key for clientVersion
     */
    public static final String CLIENT_VERSION_KEY = "clientVersion";
    /**
     * Key for sdkVersion
     */
    public static final String SDK_VERSION_KEY = "sdk.version";
    /**
     * Key for current year
     * @deprecated Replaced by {@link #BUILD_YEAR}
     */
    @Deprecated
    public static final String CURRENT_YEAR = "sdk.buildYear";
    /**
     * Key for build-year
     */
    public static final String BUILD_YEAR = "sdk.buildYear";

    // Synchronizes access to the project message resources:
    // process-models, images, global-variables, tree-elements
    final Object projectMessageLock = new Object();

    private final NjamsMetadata metadata;

    private final NjamsProcesses processes;

    // The settings of the client
    private final ClientSettings settings;

    private final NjamsJobs jobs;

    private final NjamsCommands commands;

    private final NjamsSerializers serializers = new NjamsSerializers();

    // must be declared before all facets that receive it in their field initializer
    private final LifecycleState lifecycle = new LifecycleState();

    private final NjamsFeatures features = new NjamsFeatures(lifecycle);

    private NjamsSender sender;
    private Receiver receiver;

    /** Receiver pre-created at construction time, transferred to {@link #receiver} inside {@link #startReceiver()}. */
    private Receiver earlyReceiver;

    private NjamsConfiguration configuration;

    private final NjamsReplay replay;

    private final NjamsArgos argos;

    /**
     * Create a nJAMS client.
     *
     * @param path     the path in the tree
     * @param version  the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     *                 technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String version, String category, ClientSettings settings) {
        this.settings = settings;
        jobs = new NjamsJobs(lifecycle);
        replay = new NjamsReplay(features, jobs);
        metadata = new NjamsMetadata(path, version, category, lifecycle, projectMessageLock);
        initContainerMode();
        argos = new NjamsArgos(settings);
        configuration = new NjamsConfiguration(settings, this);
        processes = new NjamsProcesses(this, lifecycle, metadata, features, configuration, projectMessageLock);
        commands = new NjamsCommands(processes, replay, metadata, features);
        processes.createTreeElements(path, TreeElementType.CLIENT);
        metadata.printStartupBanner(settings);
        beginConnect();
    }

    /**
     * Create a nJAMS client.
     *
     * @param path           the path in the tree
     * @param version        the version of the nNJAMS client
     * @param runtimeVersion the version of the underlying runtime (eg. Mule, BW6...)
     * @param category       the category of the nJAMS client, should describe the
     *                       technology
     * @param settings       needed settings for client eg. for communication
     * @deprecated The runtime version is optional and therefore no longer a constructor
     *             parameter. Use {@link #Njams(Path, String, String, ClientSettings)} and set the
     *             runtime version via {@code njams.metadata().setRuntimeVersion(runtimeVersion)} —
     *             obtain the facet via {@link #metadata()} and call
     *             {@link NjamsMetadata#setRuntimeVersion(String)} before {@link #start()};
     *             {@link NjamsMetadata#getRuntimeVersion()} is the corresponding getter.
     */
    @Deprecated
    public Njams(Path path, String version, String runtimeVersion, String category, ClientSettings settings) {
        this(path, version, category, settings);
        metadata.setRuntimeVersionInternal(runtimeVersion);
    }

    private void initContainerMode() {
        setContainerMode(settings.getBool(NjamsSettings.PROPERTY_CONTAINER_MODE, true));
    }

    /**
     * Lenient-legacy guard: where the new facet API rejects a call after start(), the deprecated
     * facade method only logs a warning and proceeds, so that existing client code keeps working
     * throughout the deprecation period.
     */
    private void warnIfStarted(String oldMethod, String replacement) {
        if (lifecycle.isStarted()) {
            LOG.warn("{} was called after start(); the change will not be sent to the nJAMS server."
                + " The replacement API {} rejects this call.", oldMethod, replacement);
        }
    }

    /**
     * Provides access to the identifying metadata of this client: path, category, versions,
     * machine, session id, and the global variables announced to the nJAMS server at start.
     *
     * @return the metadata facet of this client, never <code>null</code>
     */
    public NjamsMetadata metadata() {
        return metadata;
    }

    /**
     * Provides access to the optional-feature list and the container-mode flag of this client.
     * Features are announced to the nJAMS server at start.
     *
     * @return the features facet of this client, never <code>null</code>
     */
    public NjamsFeatures features() {
        return features;
    }

    /**
     * Provides access to the process models, taxonomy tree, images and process diagram tooling
     * of this client, including sending project messages.
     *
     * @return the processes facet of this client, never <code>null</code>
     */
    public NjamsProcesses processes() {
        return processes;
    }

    /**
     * Provides access to the jobs of this client: the registry of currently running
     * {@link Job} instances.
     *
     * @return the jobs facet of this client, never <code>null</code>
     */
    public NjamsJobs jobs() {
        return jobs;
    }

    /**
     * Provides access to the {@link Serializer} registry of this client, used to serialize
     * activity data to strings.
     *
     * @return the serializers facet of this client, never <code>null</code>
     */
    public NjamsSerializers serializers() {
        return serializers;
    }

    /**
     * Provides access to the replay handling of this client: registering a
     * {@link ReplayHandler} enables the replay feature.
     *
     * @return the replay facet of this client, never <code>null</code>
     */
    public NjamsReplay replay() {
        return replay;
    }

    /**
     * Provides access to the {@link InstructionListener} registry of this client, which is
     * called for commands received from the nJAMS server.
     *
     * @return the commands facet of this client, never <code>null</code>
     */
    public NjamsCommands commands() {
        return commands;
    }

    /**
     * Provides access to the Argos metric collector registration of this client.
     *
     * @return the Argos facet of this client, never <code>null</code>
     */
    public NjamsArgos argos() {
        return argos;
    }

    /**
     * Provides access to the server-driven runtime configuration of this client: log mode,
     * process exclusions, and the underlying {@link Configuration}.
     *
     * @return the configuration facet of this client, never <code>null</code>
     */
    public NjamsConfiguration configuration() {
        return configuration;
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public void addArgosCollector(ArgosMultiCollector collector) {
        argos.add(collector);
    }

    public void removeArgosCollector(ArgosMultiCollector collector) {
        argos.remove(collector);
    }

    /**
     * @return the category of the nJAMS client, which should describe the
     * technology
     * @deprecated Use {@code njams.metadata().getCategory()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getCategory()}.
     */
    @Deprecated
    public String getCategory() {
        return metadata.getCategory();
    }

    /**
     * @return the current nJAMS settings
     */
    public ClientSettings getSettings() {
        return settings;
    }

    /**
     * @return the clientPath
     * @deprecated Use {@code njams.metadata().getClientPath()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getClientPath()}.
     */
    @Deprecated
    public Path getClientPath() {
        return metadata.getClientPath();
    }

    /**
     * This is ID is used in container mode for identifying this client instance in commands.
     * @return A random ID generated during initialization.
     * @deprecated This method was a duplicate of {@link #getClientSessionId()}. Use
     *             {@code njams.metadata().getClientSessionId()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getClientSessionId()}.
     */
    @Deprecated
    public String getCommunicationSessionId() {
        return metadata.getClientSessionId();
    }

    /**
     * @return the clientVersion
     * @deprecated Use {@code njams.metadata().getClientVersion()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getClientVersion()}.
     */
    @Deprecated
    public String getClientVersion() {
        return metadata.getClientVersion();
    }

    /**
     * @return the sdkVersion
     * @deprecated Use {@code njams.metadata().getSdkVersion()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getSdkVersion()}.
     */
    @Deprecated
    public String getSdkVersion() {
        return metadata.getSdkVersion();
    }

    /**
     * @return the runtimeVersion
     * @deprecated Use {@code njams.metadata().getRuntimeVersion()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getRuntimeVersion()}.
     */
    @Deprecated
    public String getRuntimeVersion() {
        return metadata.getRuntimeVersion();
    }

    /**
     * Sets the version of the underlying runtime (eg. Mule, BW6, ...).
     *
     * @param runtimeVersion the runtime version to set
     * @deprecated Use {@code njams.metadata().setRuntimeVersion(runtimeVersion)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link NjamsMetadata#setRuntimeVersion(String)}. Unlike this method, the
     *             replacement throws an {@link NjamsSdkRuntimeException} when called after
     *             {@link #start()}, because the runtime version is announced to the nJAMS server
     *             at start and a later change is never sent.
     */
    @Deprecated
    public void setRuntimeVersion(String runtimeVersion) {
        warnIfStarted("setRuntimeVersion", "metadata().setRuntimeVersion(...)");
        metadata.setRuntimeVersionInternal(runtimeVersion);
    }

    /**
     * @return the globalVariables
     * @deprecated Use {@code njams.metadata().getGlobalVariables()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getGlobalVariables()}.
     */
    @Deprecated
    public Map<String, String> getGlobalVariables() {
        return metadata.getGlobalVariables();
    }

    /**
     * Adds the given global variables to this instance's global variables.
     *
     * @param globalVariables The global variables to be added to this instance.
     * @deprecated Use {@code njams.metadata().addGlobalVariables(globalVariables)} instead —
     *             obtain the facet via {@link #metadata()} and call
     *             {@link NjamsMetadata#addGlobalVariables(Map)}. Unlike this method, the
     *             replacement throws an {@link NjamsSdkRuntimeException} when called after
     *             {@link #start()}, because global variables are announced to the nJAMS server at
     *             start and a later change is never sent.
     */
    @Deprecated
    public void addGlobalVariables(Map<String, String> globalVariables) {
        warnIfStarted("addGlobalVariables", "metadata().addGlobalVariables(...)");
        metadata.addGlobalVariablesInternal(globalVariables);
    }

    /**
     * Returns the regular expression that defines how global-variable references are detected and replaced in this
     * client's configurations. When {@code null}, the nJAMS server applies its own default matching behavior.
     *
     * @return the global-variable matching pattern, or {@code null} if none was set
     * @deprecated Use {@code njams.metadata().getGlobalVariablesPattern()} instead — obtain the
     *             facet via {@link #metadata()} and call
     *             {@link NjamsMetadata#getGlobalVariablesPattern()}.
     */
    @Deprecated
    public String getGlobalVariablesPattern() {
        return metadata.getGlobalVariablesPattern();
    }

    /**
     * Sets the regular expression that defines how global-variable references are detected and replaced in this
     * client's configurations, overriding the nJAMS server's default matching. The pattern must use named groups:
     * {@code full} (the entire reference, e.g. {@code %%var%%}) and {@code name} (the variable name) are required;
     * {@code default} (a fallback value) and {@code optional} (any non-blank match marks the reference as optional)
     * are optional. The pattern is transported to the server with the project message.
     *
     * @param globalVariablesPattern the regex pattern, or {@code null} to clear it and let the server apply its
     *                               default behavior
     * @throws NjamsSdkRuntimeException if the pattern is not a valid regular expression or does not declare the
     *                                  required named groups {@code full} and {@code name}
     * @deprecated Use {@code njams.metadata().setGlobalVariablesPattern(pattern)} instead — obtain
     *             the facet via {@link #metadata()} and call
     *             {@link NjamsMetadata#setGlobalVariablesPattern(String)}. Unlike this method, the
     *             replacement throws an {@link NjamsSdkRuntimeException} when called after
     *             {@link #start()}, because the pattern is announced to the nJAMS server at start
     *             and a later change is never sent.
     */
    @Deprecated
    public void setGlobalVariablesPattern(String globalVariablesPattern) {
        warnIfStarted("setGlobalVariablesPattern", "metadata().setGlobalVariablesPattern(...)");
        metadata.setGlobalVariablesPatternInternal(globalVariablesPattern);
    }

    /**
     * Gets the current replay handler if present.
     *
     * @return Current replay handler if present or null otherwise.
     */
    public ReplayHandler getReplayHandler() {
        return replay.getHandler();
    }

    /**
     * Sets a replay handler.
     *
     * @param replayHandler Replay handler to be set.
     * @see AbstractReplayHandler
     */
    public void setReplayHandler(final ReplayHandler replayHandler) {
        replay.setHandler(replayHandler);
    }

    /**
     * Returns whether or not container-mode is enabled.
     * @return Returns whether or not container-mode is enabled.
     */
    public boolean isContainerMode() {
        return features.isContainerMode();
    }

    /**
     * Allows overriding the container-mode support setting.
     * This can only be changed before the client is started.
     * @param enabled <code>true</code> for enabling container-mode, <code>false</code> for disabling.
     */
    public void setContainerMode(boolean enabled) {
        features.setContainerMode(enabled);
    }

    /**
     * Adds a image for a given resource path.
     *
     * @param key          the key of the image
     * @param resourcePath the path where to find the image
     */
    public void addImage(final String key, final String resourcePath) {
        processes.addImage(key, resourcePath);
    }

    /**
     * Add an image with an arbitrary supplier implementation.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     */
    public void addImage(final ImageSupplier imageSupplier) {
        processes.addImage(imageSupplier);
    }

    /**
     * @param processDiagramFactory the processDiagramFactory to set
     */
    public void setProcessDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        processes.setDiagramFactory(processDiagramFactory);
    }

    /**
     * Returns the a Sender implementation, which is configured as specified in
     * the settings.
     *
     * @return the Sender
     */
    public NjamsSender getSender() {
        if (sender == null) {
            if (settings.getBool(NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, false)) {
                LOG.debug("Using shared sender pool for {}", getClientPath());
                sender = NjamsSender.takeSharedSender(settings);
            } else {
                LOG.debug("Creating individual sender pool for {}", getClientPath());
                sender = new NjamsSender(settings);
            }
        }
        return sender;
    }

    /**
     * Pre-creates the receiver and starts its connection attempt in the background, so that the
     * connection overlaps with the remaining application setup. Called automatically at construction
     * time. Idempotent and best-effort: any failure is swallowed and {@link #startReceiver()} will
     * retry creating the receiver.
     */
    private void beginConnect() {
        if (earlyReceiver != null || lifecycle.isStarted()) {
            return;
        }
        try {
            earlyReceiver = new CommunicationFactory(settings).getReceiver(this);
            if (earlyReceiver instanceof AbstractReceiver) {
                ((AbstractReceiver) earlyReceiver).beginConnect();
            }
        } catch (Exception e) {
            LOG.warn("beginConnect() failed to pre-initialize receiver; start() will retry.", e);
            earlyReceiver = null;
        }
    }

    /**
     * Start the receiver, which is used to retrieve instructions
     */
    private void startReceiver() {
        try {
            if (earlyReceiver != null) {
                receiver = earlyReceiver;
                earlyReceiver = null;
            } else {
                receiver = new CommunicationFactory(settings).getReceiver(this);
            }
            long timeoutMs = settings.getLong(
                NjamsSettings.PROPERTY_COMMUNICATION_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_MS);
            receiver.startWithTimeout(timeoutMs);
            if (receiver instanceof SenderExceptionListener) {
                final NjamsSender sender = getSender();
                if (sender != null) {
                    sender.addSenderExceptionListener((SenderExceptionListener) receiver);
                }
            }
        } catch (Exception e) {
            LOG.error("SDK startup failed: could not establish communication connection. "
                + "The SDK instance is inactive.", e);
            if (receiver != null) {
                try {
                    receiver.stop();
                } catch (Exception ex) {
                    LOG.debug("Unable to stop receiver after startup failure", ex);
                }
                receiver = null;
            }
        }
    }

    /**
     * Start a client; it will initiate the connections and start processing.
     *
     * @return true if successful
     */
    public boolean start() {
        if (!isStarted()) {
            if (settings == null) {
                throw new NjamsSdkRuntimeException("Settings not set");
            }
            configuration.load();
            configuration.initializeDataMasking();
            commands.add(this);
            commands.add(new ConfigurationInstructionListener(this));
            startReceiver();
            if (receiver == null) {
                return false;
            }
            LogMessageFlushTask.start(this);
            CleanTracepointsTask.start(this);
            lifecycle.setStarted(true);
            sendProjectMessage();
            LOG.info("SDK instance {} started (client-session={})", getClientPath(), metadata.getClientSessionId());
        }
        return isStarted();
    }

    /**
     * Returns a transient UUID that identifies this {@link Njams} client instance during its JVM lifetime.
     * This is internally used for (container-mode) communications.
     * @return The current ID of this client.
     * @deprecated Use {@code njams.metadata().getClientSessionId()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getClientSessionId()}.
     */
    @Deprecated
    public String getClientSessionId() {
        return metadata.getClientSessionId();
    }

    /**
     * Stop a client; it stop processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        lifecycle.requireStarted();
        LogMessageFlushTask.stop(this);
        CleanTracepointsTask.stop(this);

        argos.stop();

        if (sender != null) {
            sender.close();
        }
        if (receiver != null) {
            if (receiver instanceof ShareableReceiver) {
                ((ShareableReceiver<?>) receiver).removeNjams(this);
            } else {
                receiver.stop();
            }
        }
        commands.clear();
        lifecycle.setStarted(false);
        return !isStarted();
    }

    /**
     * Return the ProcessModel to the path;
     *
     * @param relativePath The relative path of the process model to get
     * @return the ProcessModel or {@link NjamsSdkRuntimeException}
     */
    public ProcessModel getProcessModel(final Path relativePath) {
        return processes.get(relativePath);
    }

    /**
     * Check for a process model under that path
     *
     * @param relativePath The relative path of the process model to check
     * @return true if found else false
     */
    public boolean hasProcessModel(final Path relativePath) {
        return processes.has(relativePath);
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    public Collection<ProcessModel> getProcessModels() {
        return processes.getAll();
    }

    /**
     * Returns the job instance for given jobId.
     *
     * @param jobId the jobId to search for
     * @return the Job or null if not found
     */
    public Job getJobById(final String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Returns a collection of all current jobs. This collection must not be
     * changed.
     *
     * @return Unmodifiable collection of jobs.
     */
    public Collection<Job> getJobs() {
        return jobs.getAll();
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param path Relative path to the client of the process which should be
     *             created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel createProcess(final Path path) {
        return processes.create(path, this);
    }

    /**
     * Adds a process model to this instance. The model must be build for this instance.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     */
    public void addProcessModel(final ProcessModel processModel) {
        processes.add(processModel, this);
    }

    /**
     * Flush all resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void sendProjectMessage() {
        processes.send();
    }

    /**
     * Send an additional process for an already started client.
     * This will create a small ProjectMessage only containing the new process.
     *
     * @param model the additional model to send
     */
    public void sendAdditionalProcess(final ProcessModel model) {
        processes.announce(model);
    }

    /**
     * Set the type for a TreeElment given by a path.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     */
    public void setTreeElementType(Path path, String type) {
        processes.setTreeElementType(path, type);
    }

    /**
     * @return the processModelLayouter
     */
    public ProcessModelLayouter getProcessModelLayouter() {
        return processes.getLayouter();
    }

    /**
     * @param processModelLayouter the processModelLayouter to set
     */
    public void setProcessModelLayouter(ProcessModelLayouter processModelLayouter) {
        processes.setLayouter(processModelLayouter);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        return 83 * hash + Objects.hashCode(metadata.getClientPath());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Njams other = (Njams) obj;
        // use the getter on other: mocked instances have no metadata facet
        return Objects.equals(metadata.getClientPath(), other.getClientPath());
    }

    /**
     * Adds a job to the joblist. If njams hasn't started before, it can't be
     * added to the list.
     *
     * @param job to add to the instances job list.
     */
    public void addJob(Job job) {
        jobs.add(job);
    }

    /**
     * Remove a job from the joblist
     *
     * @param jobId of the Job to be removed
     */
    public void removeJob(String jobId) {
        jobs.remove(jobId);
    }

    /**
     * Read the versions from njams.version files. Set the SDK-Version and the
     * Client-Version if found.
     *
     * @param version
     */
    /**
     * @return the instructionListeners
     */
    public List<InstructionListener> getInstructionListeners() {
        return commands.list();
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    public void addInstructionListener(InstructionListener listener) {
        commands.add(listener);
    }

    /**
     * Removes a InstructionListener from the Receiver.
     *
     * @param listener the listener to remove
     */
    public void removeInstructionListener(InstructionListener listener) {
        commands.remove(listener);
    }

    /**
     * @return the ProcessDiagramFactory
     */
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return processes.getDiagramFactory();
    }

    /**
     * Implementation of the InstructionListener interface. Listens on
     * sendProjectMessage, ping, replay and getRequestHandler.
     *
     * @param instruction The instruction which should be handled
     */
    @Override
    public void onInstruction(Instruction instruction) {
        commands.dispatch(instruction);
    }

    /**
     * Adds a {@link Serializer} for serializing a given class. <br>
     * Uses {@link #serialize(java.lang.Object) } to serialize instances of this
     * class with the registered serializer. If a serializer is already
     * registered, it will be replaced with the new serializer.
     *
     * @param <T>        Type that the given instance serializes
     * @param key        Class for which the serializer should be registered
     * @param serializer A serializer that can serialize instances of class key
     *                   to strings.
     * @return If a serializer for the same type was already registered before,
     * the former registered serializer is returned. Otherwise <code>null</code> is returned.
     */
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        return serializers.add(key, serializer);
    }

    /**
     * Removes the serialier with the given class key. If not serializer is
     * registered yet, <b>null</b> will be returned.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     */
    public <T> Serializer<T> removeSerializer(final Class<T> key) {
        return serializers.remove(key);
    }

    /**
     * Gets the serializer for exactly the given class key. If no serializer is
     * registered yet, <b>null</b> will be returned.
     * This implementation does not consider the class hierarchy. See also {@link #findSerializer(Class)}.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     * @see #findSerializer(Class)
     */
    public <T> Serializer<T> getSerializer(final Class<T> key) {
        return serializers.get(key);
    }

    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class)} with no effective
     * size limit.
     *
     * @param <T> type of the class
     * @param t   Object to be serialized
     * @return a string representation of the object, or {@code null} if {@code t} is {@code null},
     *         or {@code ""} when the serializer threw
     */
    public <T> String serialize(final T t) {
        return serializers.serialize(t);
    }

    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class)}, passing
     * {@code sizeLimit} through to the resolved {@link Serializer}.
     *
     * <p>The returned string may slightly exceed {@code sizeLimit} due to serializer-specific
     * buffering. {@code sizeLimit <= 0} or {@link Integer#MAX_VALUE} mean "no limit".</p>
     *
     * @param <T>       type of the class
     * @param t         Object to be serialized
     * @param sizeLimit Approximate maximum length of the returned string
     * @return a string representation of the object, or {@code null} if {@code t} is {@code null},
     *         or {@code ""} when the serializer threw
     */
    public <T> String serialize(final T t, final int sizeLimit) {
        return serializers.serialize(t, sizeLimit);
    }

    /**
     * Gets the serializer with the given class key. If no serializer is
     * registered yet, the superclass hierarchy will be checked recursively. If
     * neither the class nor any superclass is registered, the interface
     * hierarchy will be checked recursively. if no (super) interface is
     * registered, <b>null</b> will be returned.
     *
     * @param <T>   Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serializer or <b>null</b>.
     */
    public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
        return serializers.find(clazz);
    }

    /**
     * @return LogMode of this client
     */
    public LogMode getLogMode() {
        return configuration.getLogMode();
    }

    /**
     * @return the configuration
     */
    public Configuration getConfiguration() {
        return configuration.get();
    }

    /**
     * @return the machine name
     * @deprecated Use {@code njams.metadata().getMachine()} instead — obtain the facet via
     *             {@link #metadata()} and call {@link NjamsMetadata#getMachine()}.
     */
    @Deprecated
    public String getMachine() {
        return metadata.getMachine();
    }

    /**
     * @return the list of features this client has
     */
    public List<Feature> getFeatures() {
        return features.list();
    }

    /**
     * Adds a new feature to the feature list
     *
     * @param feature to set
     */
    public void addFeature(Feature feature) {
        features.add(feature);
    }

    /**
     * Remove a feature from the feature list
     *
     * @param feature to remove
     */
    public void removeFeature(final Feature feature) {
        features.remove(feature);
    }

    public boolean hasFeature(final Feature feature) {
        return features.has(feature);
    }

    /**
     * @return if this client instance is started
     */
    public boolean isStarted() {
        return lifecycle.isStarted();
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isExcluded(Path processPath) {
        return configuration.isExcluded(processPath);
    }
}
