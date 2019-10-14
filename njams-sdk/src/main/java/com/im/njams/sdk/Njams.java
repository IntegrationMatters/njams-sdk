/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import static java.util.stream.Collectors.toList;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import com.im.njams.sdk.argos.ArgosCollector;
import com.im.njams.sdk.argos.ArgosSender;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
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
import com.im.njams.sdk.communication.ReplayRequest;
import com.im.njams.sdk.communication.ReplayResponse;
import com.im.njams.sdk.communication.Sender;
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
import com.im.njams.sdk.serializer.StringSerializer;
import com.im.njams.sdk.settings.Settings;

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
     * Static value for feature replay
     */
    public static final String FEATURE_REPLAY = "replay";
    /**
     * Static value for feature inject
     */
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

    private static final Serializer<Object> DEFAULT_SERIALIZER = new StringSerializer<>();
    private static final Serializer<Object> NO_SERIALIZER = o -> null;

    private final String category;

    // Path -> ProcessModel
    private final Map<String, ProcessModel> processModels = new HashMap<>();

    // Images
    private final Collection<ImageSupplier> images = new HashSet<>();

    // Name -> Value
    private final Map<String, String> globalVariables = new HashMap<>();

    private final Map<String, String> versions = new HashMap<>();

    // The path of the client
    private final Path clientPath;

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
    private final List<String> features = new ArrayList<>();

    // TODO: implement pooling
    private Sender sender;
    private Receiver receiver;

    private Configuration configuration;
    private String machine;
    private boolean started = false;
    private static final String NOT_STARTED_EXCEPTION_MESSAGE = "The instance needs to be started first!";

    private ReplayHandler replayHandler = null;

    private ArgosSender argosSender;

    /**
     * Create a nJAMS client.
     *
     * @param path the path in the tree
     * @param version the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     * technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String version, String category, Settings settings) {
        treeElements = new ArrayList<>();
        clientPath = path;
        this.category = category == null ? null : category.toUpperCase();
        startTime = DateTimeUtility.now();
        this.settings = settings;
        processDiagramFactory = new NjamsProcessDiagramFactory();
        processModelLayouter = new SimpleProcessModelLayouter();
        argosSender = new ArgosSender(settings);
        loadConfigurationProvider();
        createTreeElements(path, TreeElementType.CLIENT);
        readVersions(version);
        printStartupBanner();
        setMachine();
    }

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    public void addArgosCollector(ArgosCollector collector){
        argosSender.addArgosCollector(collector);
    }

    /**
     * Load the ConfigurationProvider via the provided Properties
     */
    private void loadConfigurationProvider() {
        Properties properties = settings.getProperties();
        if (!properties.containsKey(ConfigurationProviderFactory.CONFIGURATION_PROVIDER)) {
            settings.getProperties().put(ConfigurationProviderFactory.CONFIGURATION_PROVIDER, DEFAULT_CACHE_PROVIDER);
        }
        ConfigurationProvider configurationProvider =
                new ConfigurationProviderFactory(properties, this).getConfigurationProvider();
        configuration = new Configuration();
        configuration.setConfigurationProvider(configurationProvider);
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
     *
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
     * @return the globalVariables
     */
    public Map<String, String> getGlobalVariables() {
        return globalVariables;
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
     */
    public void setReplayHandler(final ReplayHandler replayHandler) {
        this.replayHandler = replayHandler;
        if (replayHandler == null) {
            removeFeature(FEATURE_REPLAY);
        } else {
            addFeature(FEATURE_REPLAY);
        }
    }

    /**
     * Adds a image for a given resource path.
     *
     * @param key the key of the image
     * @param resourcePath the path where to find the image
     */
    public void addImage(final String key, final String resourcePath) {
        images.add(new ResourceImageSupplier(key, resourcePath));
    }

    /**
     * Add an image with an arbitrary supplier implementation.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     */
    public void addImage(final ImageSupplier imageSupplier) {
        images.add(imageSupplier);
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
            // sender = new CommunicationFactory(this, settings).getSender();
            sender = new NjamsSender(this, settings);
        }
        return sender;
    }

    /**
     * Start the receiver, which is used to retrieve instructions
     */
    private void startReceiver() {
        try {
            receiver = new CommunicationFactory(this, settings).getReceiver();
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
     * @return true if successfull
     */
    public boolean start() {
        if (!isStarted()) {
            if (settings == null) {
                throw new NjamsSdkRuntimeException("Settings not set");
            }
            loadConfiguration();
            initializeDataMasking();
            instructionListeners.add(this);
            instructionListeners.add(new ConfigurationInstructionListener(getConfiguration()));
            startReceiver();
            LogMessageFlushTask.start(this);
            CleanTracepointsTask.start(this);
            started = true;
            flushResources();
            argosSender.start();
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
            argosSender.close();
            if (sender != null) {
                sender.close();
            }
            if (receiver != null) {
                receiver.stop();
            }
            instructionListeners.clear();
            started = false;
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
        final List<String> parts =
                Stream.of(getClientPath(), path).map(Path::getParts).flatMap(List::stream).collect(toList());
        final ProcessModel processModel = processModels.get(new Path(parts).toString());
        if (processModel == null) {
            throw new NjamsSdkRuntimeException("ProcessModel not found for path " + path);
        }
        return processModel;
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    public Collection<ProcessModel> getProcessModels() {
        return Collections.unmodifiableCollection(processModels.values());
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
     * Create a process
     *
     * @param path Relative path to the client of the process which should be
     * created
     * @return the new ProcessModel or a {@link NjamsSdkRuntimeException}
     */
    public ProcessModel createProcess(final Path path) {
        final Path fullClientPath = path.addBase(clientPath);
        final ProcessModel model = new ProcessModel(fullClientPath, this);
        createTreeElements(fullClientPath, TreeElementType.PROCESS);
        processModels.put(fullClientPath.toString(), model);
        return model;
    }

    /**
     * Flush all Resources to the server by creating a new ProjectMessage. It
     * can only be flushed when the instance was started.
     */
    public void flushResources() {
        addDefaultImagesIfNeededAndAbsent();
        final ProjectMessage msg = new ProjectMessage();
        setStarters();
        msg.getTreeElements().addAll(treeElements);
        msg.setPath(clientPath.toString());
        msg.setClientVersion(versions.get(CLIENT_VERSION_KEY));
        msg.setSdkVersion(versions.get(SDK_VERSION_KEY));
        msg.setCategory(getCategory());
        msg.setStartTime(startTime);
        msg.setMachine(getMachine());
        msg.setFeatures(features);
        processModels.values().stream().map(pm -> pm.getSerializableProcessModel())
                .forEach(ipm -> msg.getProcesses().add(ipm));
        images.forEach(i -> msg.getImages().put(i.getName(), i.getBase64Image()));
        msg.getGlobalVariables().putAll(globalVariables);
        msg.setLogMode(configuration.getLogMode());
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
        boolean found = treeElements.stream().anyMatch(te -> te.getType().equals(treeDefaultType));
        if (found && images.stream().noneMatch(i -> i.getName().equals(treeDefaultType))) {
            addImage(treeDefaultType, treeDefaultIcon);
        }
    }

    /**
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    private void createTreeElements(Path path, TreeElementType targetDomainObjectType) {
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

    /**
     * Returns the default icon type for a TreeElement, based on the criterias
     * first and TreeElementType
     *
     * @param first Is this the root element
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
        TreeElement dos = treeElements.stream().filter(d -> d.getPath().equals(path.toString())).findAny().orElse(null);
        if (dos == null) {
            throw new NjamsSdkRuntimeException("Unable to find DomainObjectStructure for path " + path);
        }
        dos.setType(type);
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
        hash = 83 * hash + Objects.hashCode(clientPath);
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
        return Objects.equals(clientPath, other.clientPath);
    }

    /**
     * Adds a job to the joblist. If njams hasn't started before, it can't be
     * added to the list.
     *
     * @param job to add to the instances job list.
     */
    public void addJob(Job job) {
        if (isStarted()) {
            jobs.put(job.getJobId(), job);
        } else {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
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
        versions.entrySet().forEach(e -> LOG.info("***      " + e.getKey() + ": " + e.getValue()));
        LOG.info("*** ");
        LOG.info("***      Settings:");
        settings.printPropertiesWithoutPasswords();
        LOG.info("************************************************************");

    }

    /**
     * @return the instructionListeners
     */
    public List<InstructionListener> getInstructionListeners() {
        return Collections.unmodifiableList(instructionListeners);
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
     *
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
        if (instruction.getRequest().getCommand().equals(Command.SEND_PROJECTMESSAGE.commandString())) {
            flushResources();
            Response response = new Response();
            response.setResultCode(0);
            response.setResultMessage("Successfully send ProjectMessage via NjamsClient");
            instruction.setResponse(response);
            LOG.debug("Sent ProjectMessage requested via Instruction via Njams");
        } else if (instruction.getRequest().getCommand().equalsIgnoreCase(Command.REPLAY.commandString())) {
            final Response response = new Response();
            if (replayHandler != null) {
                try {
                    ReplayResponse replayResponse = replayHandler.replay(new ReplayRequest(instruction));
                    replayResponse.addParametersToInstruction(instruction);
                } catch (final Exception ex) {
                    response.setResultCode(2);
                    response.setResultMessage("Error while executing replay: " + ex.getMessage());
                    instruction.setResponse(response);
                    instruction.setResponseParameter("Exception", String.valueOf(ex));
                }
            } else {
                response.setResultCode(1);
                response.setResultMessage("Client cannot replay processes. No replay handler is present.");
                instruction.setResponse(response);
            }
        }
    }

    /**
     * Adds a {@link Serializer} for a given class. <br>
     * User {@link #serialize(java.lang.Object) } to serialize instance of this
     * class with the registered serializer. If a serializer is already
     * registered, it will be replaced with the new serializer.
     *
     * @param <T> Type of the serializer
     * @param key Class for which the serializer should be registered
     * @param serializer A serializer that can serialize instances of class key
     * to strings.
     * @return The given serializer, or if one was already registered before,
     * the former registered serializer.
     */
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        synchronized (cachedSerializers) {
            if (key != null && serializer != null) {
                cachedSerializers.clear();
                return Serializer.class.cast(serializers.put(key, serializer));
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
                return Serializer.class.cast(serializers.remove(key));
            }
            return null;
        }
    }

    /**
     * Gets the serialier with the given class key. If not serializer is
     * registered yet, <b>null</b> will be returned.
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     */
    public <T> Serializer<T> getSerializer(final Class<T> key) {
        if (key != null) {
            return Serializer.class.cast(serializers.get(key));
        }
        return null;
    }

    /**
     * Serializes a given object using {@link #findSerializer(java.lang.Class) }
     *
     * @param <T> type of the class
     * @param t Object to be serialied.
     * @return a string representation of the object.
     */
    public <T> String serialize(final T t) {

        if (t == null) {
            return null;
        }

        final Class<? super T> clazz = Class.class.cast(t.getClass());
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
     * Gets the serialier with the given class key. If not serializer is
     * registered yet, the superclass hierarchy will be checked recursivly. If
     * neither the class nor any superclass if registered, the interface
     * hierarchy will be checked recursivly. if no (super) interface is
     * registed, <b>null</b> will be returned.
     *
     * @param <T> Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serizalier of <b>null</b>.
     */
    public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
        Serializer<? super T> serializer = getSerializer(clazz);
        if (serializer == null) {
            Serializer<?> cached = cachedSerializers.get(clazz);
            if (cached == NO_SERIALIZER) {
                return null;
            } else if (cached != null) {
                return Serializer.class.cast(cached);
            }
            final Class<? super T> superclass = clazz.getSuperclass();
            if (superclass != null) {
                serializer = findSerializer(superclass);
            }
        }
        if (serializer == null) {
            final Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length && serializer == null; i++) {
                final Class<? super T> anInterface = Class.class.cast(interfaces[i]);
                serializer = Serializer.class.cast(findSerializer(anInterface));
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
     *
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
     *
     * @return the list of features this client has
     */
    public List<String> getFeatures() {
        return Collections.unmodifiableList(features);
    }

    /**
     * Adds a new feature to the feature list
     *
     * @param feature to set
     */
    public void addFeature(String feature) {
        if (!features.contains(feature)) {
            features.add(feature);
        }
    }

    /**
     * Remove a feature from the feature list
     *
     * @param feature to remove
     */
    public void removeFeature(final String feature) {
        features.remove(feature);
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
        DataMasking.addPatterns(configuration.getDataMasking());
    }
}
