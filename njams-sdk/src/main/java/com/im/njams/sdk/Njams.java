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
import com.im.njams.sdk.communication.CommunicationFactory;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.NjamsSender;
import com.im.njams.sdk.communication.Receiver;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.communication.ShareableReceiver;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ConfigurationInstructionListener;
import com.im.njams.sdk.configuration.ConfigurationProvider;
import com.im.njams.sdk.configuration.ConfigurationProviderFactory;
import com.im.njams.sdk.configuration.ProcessConfiguration;
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
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.im.njams.sdk.NjamsState.NOT_STARTED_EXCEPTION_MESSAGE;
import static java.util.stream.Collectors.toList;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 *
 * @author bwand
 */
public class Njams implements InstructionListener{

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
    private final NjamsMetadata instanceMetadata;
    private final NjamsSerializers serializers;
    private final NjamsJobs njamsJobs;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;


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
        PING("ping");

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
     * Static value for feature inject
     *
     * @deprecated Use {@link Feature#key()} on instance {@link Feature#INJECTION} instead.
     */
    @Deprecated
    public static final String FEATURE_INJECTION = "injection";

    /**
     * Key for clientVersion
     */
    public static final String CLIENT_VERSION_KEY = "clientVersion";
    /**
     * Key for sdkVersion
     */
    public static final String SDK_VERSION_KEY = "sdkVersion";

    /**
     * Key for current year
     */
    public static final String CURRENT_YEAR = "currentYear";


    // Also used for synchronizing access to the project message resources:
    // process-models, images, global-variables, tree-elements
    // Path -> ProcessModel
    private final Map<String, ProcessModel> processModels = new HashMap<>();

    // Images
    private final Collection<ImageSupplier> images = new HashSet<>();

    // Name -> Value
    private final Map<String, String> globalVariables = new HashMap<>();

    private final Map<String, String> versions = new HashMap<>();

    // The settings of the client
    private final Settings settings;

    // The start time of the engine
    private final LocalDateTime startTime;

    // tree representation for the client
    private final List<TreeElement> treeElements;

    private ProcessDiagramFactory processDiagramFactory;

    private ProcessModelLayouter processModelLayouter;


    private NjamsSender sender;
    private Receiver receiver;

    private Configuration configuration;


    private ArgosSender argosSender = null;
    private final Collection<ArgosMultiCollector<?>> argosCollectors = new ArrayList<>();

    /**
     * default secure processing
     */
    private static final String DEFAULT_DISABLE_SECURE_PROCESSING = "false";

    /**
     * Create a nJAMS client.
     *
     * @param path     the path in the tree
     * @param version  the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     *                 technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String version, String category, Settings settings) {
        treeElements = new ArrayList<>();
        startTime = DateTimeUtility.now();
        this.settings = settings;
        this.processDiagramFactory = createProcessDiagramFactory();
        processModelLayouter = new SimpleProcessModelLayouter();
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        loadConfigurationProvider();
        createTreeElements(path, TreeElementType.CLIENT);
        readVersions(version);
        this.instanceMetadata = NjamsMetadataFactory.createMetadataFor(path, versions.get(CLIENT_VERSION_KEY), versions.get(SDK_VERSION_KEY), category);
        printStartupBanner();
        serializers = new NjamsSerializers();
        njamsState = new NjamsState();
        njamsFeatures = new NjamsFeatures();
        njamsJobs = new NjamsJobs(njamsState, njamsFeatures);
    }

    private ProcessDiagramFactory createProcessDiagramFactory() {
        ProcessDiagramFactory factory;
        if(shouldProcessDiagramsBeSecure()){
            factory = NjamsProcessDiagramFactory.createSecureDiagramFactory();
        }else{
            factory = NjamsProcessDiagramFactory.createNotSecureDiagramFactory();
        }
        return factory;
    }

    private boolean shouldProcessDiagramsBeSecure(){
        return !("true".equalsIgnoreCase(getSettings().getProperty(
            Settings.PROPERTY_DISABLE_SECURE_PROCESSING,
            DEFAULT_DISABLE_SECURE_PROCESSING)));
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
     * @See getNjamsMetadata().category
     */
    @Deprecated
    public String getCategory() {
        return getNjamsMetadata().category;
    }

    /**
     * @return the current nJAMS settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the clientPath
     * @See getNjamsMetadata().clientPath
     */
    @Deprecated
    public Path getClientPath() {
        return getNjamsMetadata().clientPath;
    }

    public NjamsMetadata getNjamsMetadata(){
        return instanceMetadata;
    }

    /**
     * @return the clientVersion
     * @See getNjamsMetadata().clientVersion
     */
    @Deprecated
    public String getClientVersion() {
        return getNjamsMetadata().clientVersion;
    }

    /**
     * @return the sdkVersion
     * @See getNjamsMetadata().sdkVersion
     */
    @Deprecated
    public String getSdkVersion() {
        return getNjamsMetadata().sdkVersion;
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
     * @See getJobs().getReplayHandler()
     */
    @Deprecated
    public ReplayHandler getReplayHandler() {
        return njamsJobs.getReplayHandler();
    }

    /**
     * @See getJobs().setReplayHandler(...)
     */
    @Deprecated
    public void setReplayHandler(final ReplayHandler replayHandler) {
        njamsJobs.setReplayHandler(replayHandler);
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
    public Sender getSender() {
        if (sender == null) {
            if ("true".equalsIgnoreCase(settings.getProperty(Settings.PROPERTY_SHARED_COMMUNICATIONS))) {
                LOG.debug("Using shared sender pool for {}", getNjamsMetadata().clientPath);
                sender = NjamsSender.takeSharedSender(settings);
            } else {
                LOG.debug("Creating individual sender pool for {}", getNjamsMetadata().clientPath);
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
            receiver = new CommunicationFactory(settings).getReceiver(getNjamsMetadata());
            receiver.addInstructionListener(this);
            receiver.addInstructionListener(njamsJobs);
            receiver.addInstructionListener(new ConfigurationInstructionListener(getConfiguration()));
            receiver.start();
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
            startReceiver();
            LogMessageFlushTask.start(this);
            CleanTracepointsTask.start(this);
            njamsState.start();
            sendProjectMessage();
        }
        return isStarted();
    }

    /**
     * Stop a client; it stop processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        if (isStarted()) {
            LogMessageFlushTask.stop(this);
            CleanTracepointsTask.stop(this);

            argosCollectors.forEach(argosSender::removeArgosCollector);
            argosCollectors.clear();

            if (sender != null) {
                sender.close();
            }
            if (receiver != null) {
                if (receiver instanceof ShareableReceiver) {
                    ((ShareableReceiver) receiver).removeReceiver(receiver);
                } else {
                    receiver.stop();
                }
            }
            receiver.removeAllInstructionListeners();
            njamsState.stop();
        } else {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
        return !isStarted();
    }

    /**
     * Return the ProcessModel to the path;
     *
     * @param path the path where to get the ProcessModel from
     * @return the ProcessModel or {@link NjamsSdkRuntimeException}
     */
    public ProcessModel getProcessModel(final Path path) {
        if (!hasProcessModel(path)) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + path);
        }

        final List<String> parts = Stream.of(getNjamsMetadata().clientPath, path).map(Path::getParts).flatMap(List::stream)
            .collect(toList());
        synchronized (processModels) {
            final ProcessModel processModel = processModels.get(new Path(parts).toString());
            return processModel;
        }
    }

    /**
     * Check for a process model under that path
     *
     * @param path the path where to search for a {@link ProcessModel}.
     * @return true if found else false
     */
    public boolean hasProcessModel(final Path path) {
        if (path == null) {
            return false;
        }
        final List<String> parts = Stream.of(getNjamsMetadata().clientPath, path).map(Path::getParts).flatMap(List::stream)
            .collect(toList());
        synchronized (processModels) {
            final ProcessModel processModel = processModels.get(new Path(parts).toString());
            return processModel != null;
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
     * @See getJobs().get(...)
     */
    @Deprecated
    public Job getJobById(final String jobId) {
        return njamsJobs.get(jobId);
    }

    /**
     * @See getJobs().get()
     */
    @Deprecated
    public Collection<Job> getJobs() {
        return njamsJobs.get();
    }

    /**
     * Create a process and add it to this instance.
     *
     * @param path Relative path to the client of the process which should be
     *             created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel createProcess(final Path path) {
        final Path fullClientPath = path.addBase(getNjamsMetadata().clientPath);
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
        final List<String> clientParts = getNjamsMetadata().clientPath.getParts();
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
     * Flush all Resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void sendProjectMessage() {
        addDefaultImagesIfNeededAndAbsent();
        final ProjectMessage msg = new ProjectMessage();
        setStarters();
        msg.getTreeElements().addAll(treeElements);
        msg.setPath(getNjamsMetadata().clientPath.toString());
        msg.setClientVersion(getNjamsMetadata().clientVersion);
        msg.setSdkVersion(getNjamsMetadata().sdkVersion);
        msg.setCategory(getNjamsMetadata().category);
        msg.setStartTime(startTime);
        msg.setMachine(getNjamsMetadata().machine);
        msg.setFeatures(njamsFeatures.get());
        msg.setLogMode(configuration.getLogMode());
        synchronized (processModels) {
            processModels.values().stream().map(ProcessModel::getSerializableProcessModel)
                .forEach(ipm -> msg.getProcesses().add(ipm));
            images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
            msg.getGlobalVariables().putAll(globalVariables);
            LOG.debug("Sending project message with {} process-models, {} images, {} global-variables.",
                processModels.size(), images.size(), globalVariables.size());
        }
        getSender().send(msg);
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
     * Sets the TreeElements starter to true if the corresponding processModel
     * is a starter
     */
    private void setStarters() {
        treeElements.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS)
            .filter(te -> processModels.get(te.getPath()).isStarter()).forEach(te -> te.setStarter(true));
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
        final ProjectMessage msg = new ProjectMessage();
        msg.setPath(getNjamsMetadata().clientPath.toString());
        msg.setClientVersion(getNjamsMetadata().clientVersion);
        msg.setSdkVersion(getNjamsMetadata().sdkVersion);
        msg.setCategory(getNjamsMetadata().category);
        msg.setStartTime(startTime);
        msg.setMachine(getNjamsMetadata().machine);
        msg.setFeatures(njamsFeatures.get());
        msg.setLogMode(configuration.getLogMode());

        addTreeElements(msg.getTreeElements(), getNjamsMetadata().clientPath, TreeElementType.CLIENT, false);
        addTreeElements(msg.getTreeElements(), model.getPath(), TreeElementType.PROCESS, model.isStarter());

        msg.getProcesses().add(model.getSerializableProcessModel());
        getSender().send(msg);
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
        hash = 83 * hash + Objects.hashCode(getNjamsMetadata().clientPath);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Njams other = (Njams) obj;
        return Objects.equals(getNjamsMetadata().clientPath, other.getNjamsMetadata().clientPath);
    }

    /**
     * @See getNjamsJobs().add(...)
     */
    @Deprecated
    public void addJob(Job job) {
        njamsJobs.add(job);
    }

    /**
     * @See getNjamsJobs().remove(...)
     */
    @Deprecated
    public void removeJob(String jobId) {
        njamsJobs.remove(jobId);
    }

    public NjamsJobs getNjamsJobs(){
        return njamsJobs;
    }

    /**
     * Read the versions from njams.version files. Set the SDK-Version and the
     * Client-Version if found.
     *
     * @param version
     */
    private void readVersions(String version) {
        try {
            final Enumeration<URL> urls = this.getClass().getClassLoader().getResources("njams.version");
            while (urls.hasMoreElements()) {
                final Properties prop = new Properties();
                prop.load(urls.nextElement().openStream());
                prop.entrySet().forEach(e -> versions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
            }
        } catch (Exception e) {
            LOG.error("Unable to load versions from njams.version files", e);
        }
        if (version != null && !versions.containsKey(CLIENT_VERSION_KEY)) {
            LOG.debug("No njams.version file for {} found!", CLIENT_VERSION_KEY);
            versions.put(CLIENT_VERSION_KEY, version);
        }
        if (!versions.containsKey(SDK_VERSION_KEY)) {
            LOG.debug("No njams.version file for {} found!", SDK_VERSION_KEY);
            versions.put(SDK_VERSION_KEY, "4.0.0.alpha");
        }
    }

    private void printStartupBanner() {
        LOG.info("************************************************************");
        LOG.info("***      nJAMS SDK: Copyright (c) " + versions.get(CURRENT_YEAR) + " Faiz & Siegeln Software GmbH");
        LOG.info("*** ");
        LOG.info("***      Version Info:");
        versions.entrySet().stream().filter(e -> !CURRENT_YEAR.equals(e.getKey()))
            .sorted(Comparator.comparing(Entry::getKey))
            .forEach(e -> LOG.info("***      " + e.getKey() + ": " + e.getValue()));
        LOG.info("*** ");
        LOG.info("***      Settings:");

        settings.printPropertiesWithoutPasswords(LOG);
        LOG.info("************************************************************");

    }

    /**
     * @return the instructionListeners
     */
    public List<InstructionListener> getInstructionListeners() {
        return receiver.getInstructionListeners();
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    public void addInstructionListener(InstructionListener listener) {
        receiver.addInstructionListener(listener);
    }

    /**
     * Removes a InstructionListener from the Receiver.
     *
     * @param listener the listener to remove
     */
    public void removeInstructionListener(InstructionListener listener) {
        receiver.removeInstructionListener(listener);
    }

    /**
     * @return the ProcessDiagramFactory
     */
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return processDiagramFactory;
    }

    /**
     * Implementation of the InstructionListener interface. Listens on
     * sendProjectMessage and Replay.
     *
     * @param instruction The instruction which should be handled
     */
    @Override
    public void onInstruction(Instruction instruction) {
        if (Command.SEND_PROJECTMESSAGE.commandString().equalsIgnoreCase(instruction.getCommand())) {
            sendProjectMessage();
            final Response response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Successfully sent ProjectMessage via NjamsClient");
            instruction.setResponse(response);
            LOG.debug("Sent ProjectMessage requested via Instruction via Njams");
        } else if (Command.PING.commandString().equalsIgnoreCase(instruction.getCommand())) {
            instruction.setResponse(createPingResponse());
        }
    }

    private Response createPingResponse() {
        final Response response = new Response();
        response.setResultCode(0);
        response.setResultMessage("Pong");
        final Map<String, String> params = response.getParameters();
        params.put("clientPath", getNjamsMetadata().clientPath.toString());
        params.put("clientVersion", getNjamsMetadata().clientVersion);
        params.put("sdkVersion", getNjamsMetadata().sdkVersion);
        params.put("category", getNjamsMetadata().category);
        params.put("machine", getNjamsMetadata().machine);
        params.put("features", njamsFeatures.get().stream().collect(Collectors.joining(",")));
        return response;
    }

    public NjamsSerializers getSerializers(){
        return serializers;
    }

    /**
     * @See getSerializers().addSerializer(...)
     */
    @Deprecated
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        return serializers.add(key, serializer);
    }

    /**
     * @See getSerializers().removeSerializer(...)
     */
    @Deprecated
    public <T> Serializer<T> removeSerializer(final Class<T> key) {
        return serializers.remove(key);
    }

    /**
     * @See getSerializers().getSerializer(...)
     */
    @Deprecated
    public <T> Serializer<T> getSerializer(final Class<T> key) {
        return serializers.get(key);
    }

    /**
     * @See getSerializers().serialize(...)
     */
    @Deprecated
    public <T> String serialize(final T t) {
        return serializers.serialize(t);
    }

    /**
     * @See getSerializers().findSerializer(...)
     */
    @Deprecated
    public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
        return serializers.find(clazz);
    }

    /**
     * @return LogMode of this client
     */
    @Deprecated
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
     * @See getNjamsMetadata().machine
     */
    @Deprecated
    public String getMachine() {
        return getNjamsMetadata().machine;
    }

    /**
     * @See getNjamsFeatures().get()
     */
    @Deprecated
    public List<String> getFeatures() {
        return njamsFeatures.get();
    }

    /**
     * @See getNjamsFeatures().add()
     */
    @Deprecated
    public void addFeature(String feature) {
        njamsFeatures.add(feature);
    }

    /**
     * @See getNjamsFeatures().add()
     */
    @Deprecated
    public void addFeature(Feature feature) {
        njamsFeatures.add(feature);
    }

    /**
     * @See getNjamsFeatures().remove()
     */
    @Deprecated
    public void removeFeature(final String feature) {
        njamsFeatures.remove(feature);
    }

    /**
     * @See getNjamsFeatures().remove()
     */
    @Deprecated
    public void removeFeature(final Feature feature) {
        njamsFeatures.remove(feature);
    }

    /**
     * @See getNjamsState().isStarted()
     */
    @Deprecated
    public boolean isStarted() {
        return njamsState.isStarted();
    }

    public NjamsState getNjamsState(){
        return njamsState;
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isExcluded(Path processPath) {
        if (processPath == null) {
            return false;
        }
        if (getConfiguration().getLogMode() == LogMode.NONE) {
            return true;
        }
        ProcessConfiguration processConfiguration = getConfiguration().getProcess(processPath.toString());
        return processConfiguration != null && processConfiguration.isExclude();
    }

    /**
     * Initialize the datamasking feature
     */
    private void initializeDataMasking() {

        Properties properties = settings.getAllProperties();
        boolean dataMaskingEnabled = true;
        if (properties != null) {
            dataMaskingEnabled = Boolean.parseBoolean(properties.getProperty(DataMasking.DATA_MASKING_ENABLED, "true"));
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
                DataMasking.DATA_MASKING_ENABLED, DataMasking.DATA_MASKING_REGEX_PREFIX);
            DataMasking.addPatterns(configuration.getDataMasking());
        }
    }
}
