/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.client.LogMessageFlushTask;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.AbstractReplayHandler;
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.SenderExceptionListener;
import com.im.njams.sdk.communication.ShareableReceiver;
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
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.serializer.StringSerializer;
import com.im.njams.sdk.settings.Settings;
import com.im.njams.sdk.utils.StringUtils;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 *
 * @author bwand
 */
public class Njams implements InstructionListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);

    private static final String DEFAULT_TAXONOMY_ROOT_TYPE = "njams.taxonomy.root";
    private static final String DEFAULT_TAXONOMY_FOLDER_TYPE = "njams.taxonomy.folder";
    private static final String DEFAULT_TAXONOMY_CLIENT_TYPE = "njams.taxonomy.client";
    private static final String DEFAULT_TAXONOMY_PROCESS_TYPE = "njams.taxonomy.process";
    private static final String DEFAULT_TAXONOMY_ROOT_ICON = "images/root.png";
    private static final String DEFAULT_TAXONOMY_FOLDER_ICON = "images/folder.png";
    private static final String DEFAULT_TAXONOMY_CLIENT_ICON = "images/client.png";
    private static final String DEFAULT_TAXONOMY_PROCESS_ICON = "images/process.png";

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;

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
        private static final Collection<Feature> INHERENT_FEATURES = Collections.unmodifiableCollection(
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

    private static final String[] VERSION_FILES = { "njams.version", "msg.version", "client.version" };

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

    private static final Serializer<Object> DEFAULT_SERIALIZER = new StringSerializer<>();
    private static final Serializer<Object> NO_SERIALIZER = o -> null;

    private final String category;

    // Also used for synchronizing access to the project message resources:
    // process-models, images, global-variables, tree-elements
    // Path -> ProcessModel
    private final Map<String, ProcessModel> processModels = new HashMap<>();

    // Images
    private final Collection<ImageSupplier> images = new HashSet<>();

    // Name -> Value
    private final Map<String, String> globalVariables = new HashMap<>();

    private final Map<String, String> versions = new HashMap<>();

    // The path of the client
    private final Path clientPath;

    // The unique client id, will be generated as UUID on startup
    private final String clientSessionId;

    // The settings of the client
    private final Settings settings;

    // The start time of the engine
    private final LocalDateTime startTime;

    // tree representation for the client
    private final List<TreeElement> treeElements;

    private ProcessDiagramFactory processDiagramFactory;

    private ProcessModelLayouter processModelLayouter;

    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();

    private final List<InstructionListener> instructionListeners = new ArrayList<>();

    // serializers
    private final HashMap<Class<?>, Serializer<?>> serializers = new HashMap<>();

    // serializers
    private final HashMap<Class<?>, Serializer<?>> cachedSerializers = new HashMap<>();

    // features
    private final List<Feature> features = new CopyOnWriteArrayList<>(Feature.INHERENT_FEATURES);

    private NjamsSender sender;
    private Receiver receiver;

    private Configuration configuration;
    private String machine;
    private String runtimeVersion;
    private boolean started = false;
    private boolean containerMode = true;
    private static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private ReplayHandler replayHandler = null;
    // logId of replayed job -> deep-trace flag from the replay request
    private final Map<String, Boolean> replayedLogIds = new HashMap<>();

    private ArgosSender argosSender = null;
    private final Collection<ArgosMultiCollector<?>> argosCollectors = new ArrayList<>();

    /**
     * Create a nJAMS client without the information about the runtimeVersion of the client.
     *
     * @param path     the path in the tree
     * @param version  the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     *                 technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String version, String category, Settings settings) {
        this(path, version, null, category, settings);
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
     */
    public Njams(Path path, String version, String runtimeVersion, String category, Settings settings) {
        treeElements = new ArrayList<>();
        clientPath = path;
        this.category = category == null ? null : category.toUpperCase();
        startTime = DateTimeUtility.now();
        this.settings = settings;
        clientSessionId = UUID.randomUUID().toString();
        initContainerMode();
        processDiagramFactory = new NjamsProcessDiagramFactory(this);
        processModelLayouter = new SimpleProcessModelLayouter();
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        loadConfigurationProvider();
        createTreeElements(path, TreeElementType.CLIENT);
        readVersionsFromVersionFile(version);
        this.runtimeVersion = runtimeVersion;

        printStartupBanner();
        setMachine();
    }

    private void initContainerMode() {
        setContainerMode(!"false".equalsIgnoreCase(settings.getProperty(NjamsSettings.PROPERTY_CONTAINER_MODE)));
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public void addArgosCollector(ArgosMultiCollector collector) {
        argosCollectors.add(collector);
        argosSender.addArgosCollector(collector);
    }

    public void removeArgosCollector(ArgosMultiCollector collector) {
        argosCollectors.remove(collector);
        argosSender.removeArgosCollector(collector);
    }

    /**
     * Load the ConfigurationProvider via the provided Properties
     */
    private void loadConfigurationProvider() {
        if (!settings.containsKey(ConfigurationProviderFactory.CONFIGURATION_PROVIDER)) {
            settings.put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, DEFAULT_CACHE_PROVIDER);
        }
        ConfigurationProvider configurationProvider = new ConfigurationProviderFactory(settings, this)
                .getConfigurationProvider();
        configuration = new Configuration();
        configuration.setConfigurationProvider(configurationProvider);
        settings.addSecureProperties(configurationProvider.getSecureProperties());

    }

    /**
     * load and apply configuration from configuration provider
     */
    private void loadConfiguration() {
        ConfigurationProvider configurationProvider = configuration.getConfigurationProvider();
        if (configurationProvider != null) {
            configuration = configurationProvider.loadConfiguration();
        }
    }

    /**
     * @return the category of the nJAMS client, which should describe the
     * technology
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return the current nJAMS settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the clientPath
     */
    public Path getClientPath() {
        return clientPath;
    }

    /**
     * This is ID is used in container mode for identifying this client instance in commands.
     * @return A random ID generated during initialization.
     */
    public String getCommunicationSessionId() {
        return clientSessionId;
    }

    /**
     * @return the clientVersion
     */
    public String getClientVersion() {
        return versions.get(CLIENT_VERSION_KEY);
    }

    /**
     * @return the sdkVersion
     */
    public String getSdkVersion() {
        return versions.get(SDK_VERSION_KEY);
    }

    /**
     * @return the runtimeVersion
     */
    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * @return the globalVariables
     */
    public Map<String, String> getGlobalVariables() {
        return globalVariables;
    }

    /**
     * Adds the given global variables to this instance's global variables.
     *
     * @param globalVariables The global variables to be added to this instance.
     */
    public void addGlobalVariables(Map<String, String> globalVariables) {
        synchronized (processModels) {
            this.globalVariables.putAll(globalVariables);
        }
    }

    /**
     * Gets the current replay handler if present.
     *
     * @return Current replay handler if present or null otherwise.
     */
    public ReplayHandler getReplayHandler() {
        return replayHandler;
    }

    /**
     * Sets a replay handler.
     *
     * @param replayHandler Replay handler to be set.
     * @see AbstractReplayHandler
     */
    public void setReplayHandler(final ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
        if (replayHandler == null) {
            removeFeature(Feature.REPLAY);
        } else {
            addFeature(Feature.REPLAY);
            if ("true".equalsIgnoreCase(settings.getPropertyWithDeprecationWarning(
                    NjamsSettings.PROPERTY_DISABLE_STARTDATA, NjamsSettings.OLD_DISABLE_STARTDATA))) {
                LOG.warn("Replay functionality is limited because collecting start-data "
                        + "is disabled by configuration {}=true", NjamsSettings.PROPERTY_DISABLE_STARTDATA);
            }
        }
    }

    /**
     * Returns whether or not container-mode is enabled.
     * @return Returns whether or not container-mode is enabled.
     */
    public boolean isContainerMode() {
        return containerMode;
    }

    /**
     * Allows overriding the container-mode support setting.
     * This can only be changed before the client is started.
     * @param enabled <code>true</code> for enabling container-mode, <code>false</code> for disabling.
     */
    public void setContainerMode(boolean enabled) {
        if (isStarted()) {
            throw new NjamsSdkRuntimeException("Client is already started.");
        }
        containerMode = enabled;
        if (containerMode) {
            addFeature(Feature.CONTAINER_MODE);
        } else {
            removeFeature(Feature.CONTAINER_MODE);
        }
    }

    /**
     * Adds a image for a given resource path.
     *
     * @param key          the key of the image
     * @param resourcePath the path where to find the image
     */
    public void addImage(final String key, final String resourcePath) {
        addImage(new ResourceImageSupplier(key, resourcePath));
    }

    /**
     * Add an image with an arbitrary supplier implementation.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     */
    public void addImage(final ImageSupplier imageSupplier) {
        synchronized (processModels) {
            images.add(imageSupplier);
        }
    }

    /**
     * @param processDiagramFactory the processDiagramFactory to set
     */
    public void setProcessDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        this.processDiagramFactory = processDiagramFactory;
    }

    /**
     * Returns the a Sender implementation, which is configured as specified in
     * the settings.
     *
     * @return the Sender
     */
    public NjamsSender getSender() {
        if (sender == null) {
            if ("true".equalsIgnoreCase(settings.getPropertyWithDeprecationWarning(
                    NjamsSettings.PROPERTY_SHARED_COMMUNICATIONS, NjamsSettings.OLD_SHARED_COMMUNICATIONS))) {
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
     * Start the receiver, which is used to retrieve instructions
     */
    private void startReceiver() {
        try {
            receiver = new CommunicationFactory(settings).getReceiver(this);
            receiver.start();
            if (receiver instanceof SenderExceptionListener) {
                final NjamsSender sender = getSender();
                if (sender != null) {
                    sender.addSenderExceptionListener((SenderExceptionListener) receiver);
                }
            }
        } catch (Exception e) {
            LOG.error("Error starting Receiver", e);
            try {
                receiver.stop();
            } catch (Exception ex) {
                LOG.debug("Unable to stop receiver", ex);
            }
            receiver = null;
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
            loadConfiguration();
            initializeDataMasking();
            instructionListeners.add(this);
            instructionListeners.add(new ConfigurationInstructionListener(this));
            startReceiver();
            if (receiver == null) {
                return false;
            }
            LogMessageFlushTask.start(this);
            CleanTracepointsTask.start(this);
            started = true;
            sendProjectMessage();
            LOG.info("SDK instance {} started (client-session={})", getClientPath(), clientSessionId);
        }
        return isStarted();
    }

    /**
     * Returns a transient UUID that identifies this {@link Njams} client instance during its JVM lifetime.
     * This is internally used for (container-mode) communications.
     * @return The current ID of this client.
     */
    public String getClientSessionId() {
        return clientSessionId;
    }

    /**
     * Stop a client; it stop processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        if (!isStarted()) {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
        LogMessageFlushTask.stop(this);
        CleanTracepointsTask.stop(this);

        argosCollectors.forEach(argosSender::removeArgosCollector);
        argosCollectors.clear();

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
        instructionListeners.clear();
        started = false;
        return !isStarted();
    }

    /**
     * Return the ProcessModel to the path;
     *
     * @param relativePath The relative path of the process model to get
     * @return the ProcessModel or {@link NjamsSdkRuntimeException}
     */
    public ProcessModel getProcessModel(final Path relativePath) {

        final Path absolutePath = getClientPath().add(relativePath);
        final ProcessModel pm;
        synchronized (processModels) {
            pm = processModels.get(absolutePath.toString());
        }
        if (pm == null) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + relativePath);
        }
        return pm;
    }

    /**
     * Check for a process model under that path
     *
     * @param relativePath The relative path of the process model to check
     * @return true if found else false
     */
    public boolean hasProcessModel(final Path relativePath) {
        if (relativePath == null) {
            return false;
        }
        final Path absolutePath = getClientPath().add(relativePath);
        synchronized (processModels) {
            return processModels.containsKey(absolutePath.toString());
        }
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    public Collection<ProcessModel> getProcessModels() {
        synchronized (processModels) {
            return Collections.unmodifiableCollection(processModels.values());
        }
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
        return Collections.unmodifiableCollection(jobs.values());
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param path Relative path to the client of the process which should be
     *             created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel createProcess(final Path path) {
        final Path fullClientPath = path.addBase(clientPath);
        final ProcessModel model = new ProcessModel(fullClientPath, this);
        synchronized (processModels) {
            createTreeElements(fullClientPath, TreeElementType.PROCESS);
            processModels.put(fullClientPath.toString(), model);
        }
        return model;
    }

    /**
     * Adds a process model to this instance. The model must be build for this instance.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     */
    public void addProcessModel(final ProcessModel processModel) {
        if (processModel == null) {
            return;
        }
        if (processModel.getNjams() != this) {
            throw new NjamsSdkRuntimeException("Process model has been created for a different nJAMS instance.");
        }
        final List<String> clientParts = clientPath.getParts();
        final List<String> prefix = processModel.getPath().getParts().subList(0, clientParts.size());
        if (!clientParts.equals(prefix)) {
            throw new NjamsSdkRuntimeException("Process model path does not match this nJAMS instance.");
        }
        synchronized (processModels) {
            createTreeElements(processModel.getPath(), TreeElementType.PROCESS);
            processModels.put(processModel.getPath().toString(), processModel);
        }
    }

    /**
     * Initializes the common body of a project message.
     * @return
     */
    private ProjectMessage prepareProjectMessage() {
        final ProjectMessage msg = new ProjectMessage();
        msg.setPath(clientPath.toString());
        msg.setClientVersion(versions.get(CLIENT_VERSION_KEY));
        msg.setSdkVersion(versions.get(SDK_VERSION_KEY));
        msg.setRuntimeVersion(runtimeVersion);
        msg.setCategory(getCategory());
        msg.setStartTime(startTime);
        msg.setMachine(getMachine());
        msg.setFeatures(features.stream().map(Feature::key).collect(Collectors.toList()));
        msg.setLogMode(configuration.getLogMode());
        msg.setClientId(clientSessionId);
        msg.setRecording(getConfiguration().isRecording());
        return msg;
    }

    /**
     * Flush all resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void sendProjectMessage() {
        addDefaultImagesIfNeededAndAbsent();
        setStarters();
        final ProjectMessage msg = prepareProjectMessage();
        msg.getTreeElements().addAll(treeElements);
        synchronized (processModels) {
            processModels.values().stream().map(ProcessModel::getSerializableProcessModel)
                    .forEach(ipm -> msg.getProcesses().add(ipm));
            images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
            msg.getGlobalVariables().putAll(globalVariables);
            LOG.debug("Sending project message with {} process-models, {} images, {} global-variables.",
                    processModels.size(), images.size(), globalVariables.size());
        }
        getSender().send(msg, clientSessionId);
    }

    /**
     * Send an additional process for an already started client.
     * This will create a small ProjectMessage only containing the new process.
     *
     * @param model the additional model to send
     */
    public void sendAdditionalProcess(final ProcessModel model) {
        if (!isStarted()) {
            throw new NjamsSdkRuntimeException("Njams is not started. Please use createProcess Method instead");
        }
        final ProjectMessage msg = prepareProjectMessage();
        addTreeElements(msg.getTreeElements(), getClientPath(), TreeElementType.CLIENT, false);
        addTreeElements(msg.getTreeElements(), model.getPath(), TreeElementType.PROCESS, model.isStarter());
        msg.getProcesses().add(model.getSerializableProcessModel());
        getSender().send(msg, clientSessionId);
    }

    /**
     * Adds imgages for the default keys, if they are used and no image has benn
     * added for them
     */
    private void addDefaultImagesIfNeededAndAbsent() {
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_FOLDER_TYPE, DEFAULT_TAXONOMY_FOLDER_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_ROOT_TYPE, DEFAULT_TAXONOMY_ROOT_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_CLIENT_TYPE, DEFAULT_TAXONOMY_CLIENT_ICON);
        addDefaultImagesIfNeededAndAbsent(DEFAULT_TAXONOMY_PROCESS_TYPE, DEFAULT_TAXONOMY_PROCESS_ICON);
    }

    /**
     * Checks all tree elements if the given treeDefaultType has been used, and
     * adds the treeDefaultIcon if not images has been added yet
     *
     * @param treeDefaultType type of the tree element
     * @param treeDefaultIcon icon which should be added if needed
     */
    private void addDefaultImagesIfNeededAndAbsent(String treeDefaultType, String treeDefaultIcon) {
        synchronized (processModels) {
            boolean found = treeElements.stream().anyMatch(te -> te.getType().equals(treeDefaultType));
            if (found && images.stream().noneMatch(i -> i.getName().equals(treeDefaultType))) {
                addImage(treeDefaultType, treeDefaultIcon);
            }
        }
    }

    /**
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    private void createTreeElements(Path path, TreeElementType targetDomainObjectType) {
        synchronized (processModels) {
            String currentPath = ">";
            for (int i = 0; i < path.getParts().size(); i++) {
                String part = path.getParts().get(i);
                currentPath += part + ">";
                final String finalPath = currentPath;
                boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
                if (!found) {
                    TreeElementType domainObjectType = i == path.getParts().size() - 1 ? targetDomainObjectType : null;
                    String type = getTreeElementDefaultType(i == 0, domainObjectType);
                    treeElements.add(new TreeElement(currentPath, part, type, domainObjectType));
                }
            }
        }
    }

    /**
     * Returns the default icon type for a TreeElement, based on the criterias
     * first and TreeElementType
     *
     * @param first          Is this the root element
     * @param treeElmentType The treeElementType
     * @return the icon type
     */
    private String getTreeElementDefaultType(boolean first, TreeElementType treeElmentType) {
        String type = DEFAULT_TAXONOMY_FOLDER_TYPE;
        if (first) {
            type = DEFAULT_TAXONOMY_ROOT_TYPE;
        } else if (treeElmentType == TreeElementType.CLIENT) {
            type = DEFAULT_TAXONOMY_CLIENT_TYPE;
        } else if (treeElmentType == TreeElementType.PROCESS) {
            type = DEFAULT_TAXONOMY_PROCESS_TYPE;
        }
        return type;
    }

    /**
     * Set the type for a TreeElment given by a path.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     */
    public void setTreeElementType(Path path, String type) {
        synchronized (processModels) {
            TreeElement dos = treeElements.stream().filter(d -> d.getPath().equals(path.toString())).findAny()
                    .orElse(null);
            if (dos == null) {
                throw new NjamsSdkRuntimeException("Unable to find DomainObjectStructure for path " + path);
            }
            dos.setType(type);
        }
    }

    /**
     * Sets the TreeElements starter flag according to the corresponding processModel
     */
    private void setStarters() {
        treeElements.stream().filter(t -> t.getTreeElementType() == TreeElementType.PROCESS)
                .forEach(t -> t.setStarter(
                        Optional.ofNullable(
                                processModels.get(t.getPath())).map(ProcessModel::isStarter).orElse(false)));
    }

    private List<TreeElement> addTreeElements(List<TreeElement> treeElements, Path processPath,
            TreeElementType targetDomainObjectType, boolean isStarter) {
        String currentPath = ">";
        for (int i = 0; i < processPath.getParts().size(); i++) {
            String part = processPath.getParts().get(i);
            currentPath += part + ">";
            final String finalPath = currentPath;
            boolean found = treeElements.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
            if (!found) {
                TreeElementType domainObjectType =
                        i == processPath.getParts().size() - 1 ? targetDomainObjectType : null;
                String type = getTreeElementDefaultType(i == 0, domainObjectType);
                treeElements.add(new TreeElement(currentPath, part, type, domainObjectType));
            }
        }
        treeElements.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS)
                .forEach(te -> te.setStarter(isStarter));
        return treeElements;
    }

    /**
     * @return the processModelLayouter
     */
    public ProcessModelLayouter getProcessModelLayouter() {
        return processModelLayouter;
    }

    /**
     * @param processModelLayouter the processModelLayouter to set
     */
    public void setProcessModelLayouter(ProcessModelLayouter processModelLayouter) {
        this.processModelLayouter = processModelLayouter;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        return 83 * hash + Objects.hashCode(clientPath);
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
        return Objects.equals(clientPath, other.clientPath);
    }

    /**
     * Adds a job to the joblist. If njams hasn't started before, it can't be
     * added to the list.
     *
     * @param job to add to the instances job list.
     */
    public void addJob(Job job) {
        if (!isStarted()) {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
        synchronized (replayedLogIds) {
            final Boolean deepTrace = replayedLogIds.remove(job.getLogId());
            if (deepTrace != null) {
                ReplayHandler.markAsReplayed(job);
                if (deepTrace) {
                    job.setDeepTrace(true);
                }
            }
        }
        jobs.put(job.getJobId(), job);
    }

    /**
     * Remove a job from the joblist
     *
     * @param jobId of the Job to be removed
     */
    public void removeJob(String jobId) {
        jobs.remove(jobId);
        LOG.debug("Job {} removed", jobId);
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
        if (version != null && !versions.containsKey(CLIENT_VERSION_KEY)) {
            LOG.debug("No version file for {} found!", CLIENT_VERSION_KEY);
            versions.put(CLIENT_VERSION_KEY, version);
        }
        if (!versions.containsKey(SDK_VERSION_KEY)) {
            LOG.debug("No version file for {} found!", SDK_VERSION_KEY);
            versions.put(SDK_VERSION_KEY, "5.0.0.dev");
        }
    }

    private void printStartupBanner() {
        LOG.info("************************************************************");
        LOG.info("***      nJAMS SDK: Copyright (c) " + versions.get(BUILD_YEAR) + " Integration Matters GmbH");
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
     * @return the instructionListeners
     */
    public List<InstructionListener> getInstructionListeners() {
        return new ArrayList<>(instructionListeners);
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    public void addInstructionListener(InstructionListener listener) {
        instructionListeners.add(listener);
    }

    /**
     * Removes a InstructionListener from the Receiver.
     *
     * @param listener the listener to remove
     */
    public void removeInstructionListener(InstructionListener listener) {
        instructionListeners.remove(listener);
    }

    /**
     * @return the ProcessDiagramFactory
     */
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return processDiagramFactory;
    }

    /**
     * Implementation of the InstructionListener interface. Listens on
     * sendProjectMessage, ping, replay and getRequestHandler.
     *
     * @param instruction The instruction which should be handled
     */
    @Override
    public void onInstruction(Instruction instruction) {
        final Command command = Command.getFromInstruction(instruction);
        if (command == null) {
            LOG.error("Received unsupported command {}", instruction.getCommand());
            instruction.setResponseResultCode(1);
            instruction.setResponseResultMessage("Unsupported command: " + instruction.getCommand());
            return;
        }
        switch (command) {
        case SEND_PROJECTMESSAGE:
            LOG.debug("Send ProjectMessage requested by nJAMS server.");
            sendProjectMessage();
            instruction.setResponseResultCode(0);
            instruction.setResponseResultMessage("ProjectMessage sent");
            break;
        case PING:
            instruction.setResponse(createPingResponse());
            break;
        case REPLAY:
            handleReplayRequest(instruction);
            break;
        case GET_REQUEST_HANDLER:
            instruction.setResponseResultCode(0);
            instruction.setResponseParameter("clientId", clientSessionId);
            break;
        default:
            // skip all others
            break;

        }
    }

    private void handleReplayRequest(Instruction instruction) {
        if (replayHandler != null) {
            try {
                final ReplayRequest replayRequest = new ReplayRequest(instruction);
                final ReplayResponse replayResponse = replayHandler.replay(replayRequest);
                replayResponse.addParametersToInstruction(instruction);
                if (!replayRequest.getTest()) {
                    setReplayMarker(replayResponse.getMainLogId(), replayRequest.getDeepTrace());
                    LOG.debug("Processed replay response {}", replayResponse.getMainLogId());
                }
            } catch (final Exception ex) {
                instruction.setResponseResultCode(2);
                instruction.setResponseResultMessage("Error while executing replay: " + ex.getMessage());
                instruction.setResponseParameter("Exception", String.valueOf(ex));
            }
        } else {
            instruction.setResponseResultCode(1);
            instruction.setResponseResultMessage("No replay handler registered.");
        }
    }

    private Response createPingResponse() {
        final Response response = new Response();
        response.setResultCode(0);
        response.setResultMessage("Pong");
        final Map<String, String> params = response.getParameters();
        params.put("clientPath", clientPath.toString());
        params.put("clientVersion", getClientVersion());
        params.put("clientId", clientSessionId);
        params.put("sdkVersion", getSdkVersion());
        params.put("runtimeVersion", getRuntimeVersion());
        params.put("category", getCategory());
        params.put("machine", getMachine());
        params.put("features", features.stream().map(Feature::key).collect(Collectors.joining(",")));
        return response;
    }

    /**
     * SDK-197
     *
     * @param logId
     */
    private void setReplayMarker(final String logId, boolean deepTrace) {
        if (StringUtils.isBlank(logId)) {
            return;
        }
        synchronized (replayedLogIds) {
            final Job job = getJobs().stream().filter(j -> j.getLogId().equals(logId)).findAny().orElse(null);
            if (job != null) {
                // if the job is already known, set the marker
                ReplayHandler.markAsReplayed(job);
                if (deepTrace) {
                    job.setDeepTrace(true);
                }
            } else {
                // remember the log ID for when the job is added later -> consumed by addJob(...)
                replayedLogIds.put(logId, deepTrace);
            }
        }
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
        synchronized (cachedSerializers) {
            if (key != null && serializer != null) {
                cachedSerializers.clear();
                return (Serializer) serializers.put(key, serializer);
            }
            return null;
        }
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
        synchronized (cachedSerializers) {
            if (key != null) {
                cachedSerializers.clear();
                return (Serializer) serializers.remove(key);
            }
            return null;
        }
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
        if (key != null) {
            return (Serializer) serializers.get(key);
        }
        return null;
    }

    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class) }
     *
     * @param <T> type of the class
     * @param t   Object to be serialied.
     * @return a string representation of the object.
     */
    public <T> String serialize(final T t) {

        if (t == null) {
            return null;
        }

        final Class<? super T> clazz = (Class) t.getClass();
        synchronized (cachedSerializers) {

            // search serializer
            Serializer<? super T> serializer = this.findSerializer(clazz);

            // user default serializer
            if (serializer == null) {
                serializer = DEFAULT_SERIALIZER;
                cachedSerializers.put(clazz, serializer);
            }

            try {
                return serializer.serialize(t);
            } catch (final Exception ex) {
                LOG.error("could not serialize object " + t, ex);
                return "";
            }
        }
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
        Serializer<? super T> serializer = getSerializer(clazz);
        if (serializer == null) {
            Serializer<?> cached = cachedSerializers.get(clazz);
            if (cached == NO_SERIALIZER) {
                return null;
            }
            if (cached != null) {
                return (Serializer) cached;
            }
            final Class<? super T> superclass = clazz.getSuperclass();
            if (superclass != null) {
                serializer = findSerializer(superclass);
            }
        }
        if (serializer == null) {
            final Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length && serializer == null; i++) {
                final Class<? super T> anInterface = (Class) interfaces[i];
                serializer = findSerializer(anInterface);
            }
        }
        if (serializer != null) {
            if (!cachedSerializers.containsKey(clazz)) {
                cachedSerializers.put(clazz, serializer);
            }
        } else {
            cachedSerializers.put(clazz, NO_SERIALIZER);
        }
        return serializer;
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

    /**
     * @return LogMode of this client
     */
    public LogMode getLogMode() {
        return getConfiguration().getLogMode();
    }

    /**
     * @return the configuration
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * @return the machine name
     */
    public String getMachine() {
        return machine;
    }

    /**
     * @return the list of features this client has
     */
    public List<Feature> getFeatures() {
        return new ArrayList<>(features);
    }

    /**
     * Adds a new feature to the feature list
     *
     * @param feature to set
     */
    public void addFeature(Feature feature) {
        if (!hasFeature(feature)) {
            features.add(feature);
        }
    }

    /**
     * Remove a feature from the feature list
     *
     * @param feature to remove
     */
    public void removeFeature(final Feature feature) {
        if (Feature.INHERENT_FEATURES.contains(feature)) {
            throw new NjamsSdkRuntimeException("Cannot remove inherent feature " + feature);
        }
        features.remove(feature);
    }

    public boolean hasFeature(final Feature feature) {
        return features.contains(feature);
    }

    /**
     * @return if this client instance is started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isExcluded(Path processPath) {
        return configuration.isProcessExcluded(processPath);
    }

    /**
     * Initialize the datamasking feature
     */
    private void initializeDataMasking() {

        Properties properties = settings.getAllProperties();
        boolean dataMaskingEnabled = true;
        if (properties != null) {
            dataMaskingEnabled =
                    Boolean.parseBoolean(properties.getProperty(NjamsSettings.PROPERTY_DATA_MASKING_ENABLED,
                            "true"));
            if (dataMaskingEnabled) {
                DataMasking.addPatterns(properties);
            } else {
                LOG.info("DataMasking is disabled.");
            }
        }
        if (dataMaskingEnabled && !configuration.getDataMasking().isEmpty()) {
            LOG.warn("DataMasking via the configuration is deprecated but will be used as well. Use settings " +
                    "with the properties \n{} = " +
                    "\"true\" \nand multiple \n{}<YOUR-REGEX-NAME> = <YOUR-REGEX> \nfor this.",
                    NjamsSettings.PROPERTY_DATA_MASKING_ENABLED, NjamsSettings.PROPERTY_DATA_MASKING_REGEX_PREFIX);
            DataMasking.addPatterns(configuration.getDataMasking());
        }
    }
}
