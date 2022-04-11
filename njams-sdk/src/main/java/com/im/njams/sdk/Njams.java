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
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.ArgosSender;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.client.LogMessageFlushTask;
import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.client.NjamsMetadataFactory;
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
import com.im.njams.sdk.logmessage.NjamsFeatures;
import com.im.njams.sdk.logmessage.NjamsJobs;
import com.im.njams.sdk.logmessage.NjamsState;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.layout.SimpleProcessModelLayouter;
import com.im.njams.sdk.model.svg.NjamsProcessDiagramFactory;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.im.njams.sdk.logmessage.NjamsState.NOT_STARTED_EXCEPTION_MESSAGE;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 *
 * @author bwand
 */
public class Njams implements InstructionListener{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);

    private static final String DEFAULT_CACHE_PROVIDER = FileConfigurationProvider.NAME;
    private final NjamsMetadata instanceMetadata;
    private final NjamsSerializers serializers;
    private final NjamsJobs njamsJobs;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;

    /**
     * Static value for feature inject
     *
     * @deprecated Use {@link NjamsFeatures.Feature#key()} on instance {@link NjamsFeatures.Feature#INJECTION} instead.
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

    private final Map<String, String> versions = new HashMap<>();

    // The settings of the client
    private final Settings settings;

    private final NjamsProjectMessage njamsProjectMessage;

    private ProcessDiagramFactory processDiagramFactory;

    private NjamsSender sender;
    private Receiver receiver;

    private Configuration configuration;


    private ArgosSender argosSender = null;
    private final Collection<ArgosMultiCollector<?>> argosCollectors = new ArrayList<>();


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
        this.settings = settings;
        argosSender = ArgosSender.getInstance();
        argosSender.init(settings);
        loadConfigurationProvider();
        loadConfiguration();
        readVersions(version);
        this.instanceMetadata = NjamsMetadataFactory.createMetadataFor(path, versions.get(CLIENT_VERSION_KEY),
            versions.get(SDK_VERSION_KEY), category);
        serializers = new NjamsSerializers();
        njamsState = new NjamsState();
        njamsFeatures = new NjamsFeatures();
        njamsJobs = new NjamsJobs(njamsState, njamsFeatures);
        this.njamsProjectMessage = new NjamsProjectMessage(getNjamsMetadata(), getNjamsFeatures(), getConfiguration(),
            getSender(), getNjamsState(), getNjamsJobs(), getNjamsSerializers(), getSettings());
        printStartupBanner();
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
        ConfigurationProvider configurationProvider = new ConfigurationProviderFactory(settings)
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
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#category
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
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#clientPath
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
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#clientVersion
     */
    @Deprecated
    public String getClientVersion() {
        return getNjamsMetadata().clientVersion;
    }

    /**
     * @return the sdkVersion
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#sdkVersion
     */
    @Deprecated
    public String getSdkVersion() {
        return getNjamsMetadata().sdkVersion;
    }

    /**
     * @return the globalVariables
     */
    @Deprecated
    public Map<String, String> getGlobalVariables() {
        return njamsProjectMessage.getGlobalVariables();
    }

    /**
     * Adds the given global variables to this instance's global variables.
     *
     * @param globalVariables The global variables to be added to this instance.
     */
    @Deprecated
    public void addGlobalVariables(Map<String, String> globalVariables) {
        njamsProjectMessage.addGlobalVariables(globalVariables);
    }

    /**
     * @return The handler that handles the replay of a job
     * @see #getNjamsJobs()
     * @see NjamsJobs#getReplayHandler()
     */
    @Deprecated
    public ReplayHandler getReplayHandler() {
        return njamsJobs.getReplayHandler();
    }

    /**
     * Sets the handler to handle the replay of a job
     * @param replayHandler the handler to replay the job
     * @see #getNjamsJobs()
     * @see NjamsJobs#setReplayHandler(ReplayHandler)
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
    @Deprecated
    public void addImage(final String key, final String resourcePath) {
        njamsProjectMessage.addImage(new ResourceImageSupplier(key, resourcePath));
    }

    /**
     * Add an image with an arbitrary supplier implementation.
     *
     * @param imageSupplier the supplier used by SDK to find the image
     */
    @Deprecated
    public void addImage(final ImageSupplier imageSupplier) {
        njamsProjectMessage.addImage(imageSupplier);
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
            receiver.addInstructionListener(njamsProjectMessage);
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
            initializeDataMasking();
            startReceiver();
            LogMessageFlushTask.start(getNjamsMetadata(), getNjamsJobs(), getSettings());
            CleanTracepointsTask.start(getNjamsMetadata(), getConfiguration(), getSender());
            njamsState.start();
            njamsProjectMessage.sendProjectMessage();
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
            LogMessageFlushTask.stop(getNjamsMetadata());
            CleanTracepointsTask.stop(getNjamsMetadata());

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
    @Deprecated
    public ProcessModel getProcessModel(final Path path) {
        return njamsProjectMessage.getProcessModel(path);
    }

    /**
     * Check for a process model under that path
     *
     * @param path the path where to search for a {@link ProcessModel}.
     * @return true if found else false
     */
    @Deprecated
    public boolean hasProcessModel(final Path path) {
        return njamsProjectMessage.hasProcessModel(path);
    }

    /**
     * Returns a collection of all process models
     *
     * @return Collection of all process models
     */
    @Deprecated
    public Collection<ProcessModel> getProcessModels() {
        return njamsProjectMessage.getProcessModels();
    }

    /**
     *
     * @param jobId the key to the corresponding job
     * @return the corresponding job
     * @see #getNjamsJobs()
     * @see NjamsJobs#get(String)
     */
    @Deprecated
    public Job getJobById(final String jobId) {
        return njamsJobs.get(jobId);
    }

    /**
     * @see #getNjamsJobs()
     * @see NjamsJobs#get()
     * @return A collections of jobs
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
    @Deprecated
    public ProcessModel createProcess(final Path path) {
        return njamsProjectMessage.createProcess(path);
    }

    /**
     * Adds a process model to this instance. The model must be build for this instance.
     *
     * @param processModel The model to be added. A {@link NjamsSdkRuntimeException} is thrown if the given model was
     *                     created for another instance than this.
     */
    @Deprecated
    public void addProcessModel(final ProcessModel processModel) {
        njamsProjectMessage.addProcessModel(processModel);
    }

    /**
     * Send an additional process for an already started client.
     * This will create a small ProjectMessage only containing the new process.
     *
     * @param model the additional model to send
     */
    @Deprecated
    public void sendAdditionalProcess(final ProcessModel model) {
        njamsProjectMessage.sendAdditionalProcess(model);
    }

    /**
     * Set the type for a TreeElment given by a path.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     */
    @Deprecated
    public void setTreeElementType(Path path, String type) {
        njamsProjectMessage.setTreeElementType(path, type);
    }

    /**
     * @return the processModelLayouter
     */
    @Deprecated
    public ProcessModelLayouter getProcessModelLayouter() {
        return njamsProjectMessage.getProcessModelLayouter();
    }

    /**
     * @param processModelLayouter the processModelLayouter to set
     */
    @Deprecated
    public void setProcessModelLayouter(ProcessModelLayouter processModelLayouter) {
        njamsProjectMessage.setProcessModelLayouter(processModelLayouter);
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
     * @param job the job to add, it's jobId is the key
     * @see #getNjamsJobs()
     * @see NjamsJobs#add(Job)
     */
    @Deprecated
    public void addJob(Job job) {
        njamsJobs.add(job);
    }

    /**
     * @param jobId Key to remove the job from the job collection.
     * @see #getNjamsJobs()
     * @see NjamsJobs#remove(String)
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
    @Deprecated
    public List<InstructionListener> getInstructionListeners() {
        return receiver.getInstructionListeners();
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    @Deprecated
    public void addInstructionListener(InstructionListener listener) {
        receiver.addInstructionListener(listener);
    }

    /**
     * Removes a InstructionListener from the Receiver.
     *
     * @param listener the listener to remove
     */
    @Deprecated
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
        if (Command.PING.commandString().equalsIgnoreCase(instruction.getCommand())) {
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

    public NjamsSerializers getNjamsSerializers(){
        return serializers;
    }

    /**
     *
     * @param <T>        Type that the given instance serializes
     * @param key        Class for which the serializer should be registered
     * @param serializer A serializer that can serialize instances of class key
     *                   to strings.
     * @return If a serializer for the same type was already registered before,
     * the former registered serializer is returned. Otherwise <code>null</code> is returned.
     *
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#add(Class, Serializer)
     */
    @Deprecated
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        return serializers.add(key, serializer);
    }

    /**
     * @param key a class
     * @param <T> type of serializable
     * @return Registered serializer or <b>null</b>
     *
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#remove(Class)
     */
    @Deprecated
    public <T> Serializer<T> removeSerializer(final Class<T> key) {
        return serializers.remove(key);
    }

    /**
     *
     * @param <T> type of the class
     * @param key a class
     * @return Registered serializer or <b>null</b>
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#get(Class)
     */
    @Deprecated
    public <T> Serializer<T> getSerializer(final Class<T> key) {
        return serializers.get(key);
    }

    /**
     * @param <T> type of the class
     * @param t   Object to be serialied.
     * @return a string representation of the object.
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#serialize(Object)
     */
    @Deprecated
    public <T> String serialize(final T t) {
        return serializers.serialize(t);
    }

    /**
     * @param <T>   Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serizalier of <b>null</b>.
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#find(Class)
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
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#machine
     */
    @Deprecated
    public String getMachine() {
        return getNjamsMetadata().machine;
    }

    public NjamsFeatures getNjamsFeatures(){
        return njamsFeatures;
    }

    /**
     * @see #getNjamsFeatures()
     * @see NjamsFeatures#get()
     * @return the list of features this client has
     */
    @Deprecated
    public List<String> getFeatures() {
        return njamsFeatures.get();
    }

    /**
     * @param feature to set
     * @see #getNjamsFeatures()
     * @see NjamsFeatures#add(String)
     */
    @Deprecated
    public void addFeature(String feature) {
        njamsFeatures.add(feature);
    }

    /**
     * @param feature to set
     * @see #getNjamsFeatures()
     * @see NjamsFeatures#add(NjamsFeatures.Feature)
     */
    @Deprecated
    public void addFeature(NjamsFeatures.Feature feature) {
        njamsFeatures.add(feature);
    }

    /**
     * @param feature to remove
     * @see #getNjamsFeatures()
     * @see NjamsFeatures#remove(String)
     */
    @Deprecated
    public void removeFeature(final String feature) {
        njamsFeatures.remove(feature);
    }

    /**
     * @param feature to remove
     * @see #getNjamsFeatures()
     * @see NjamsFeatures#remove(NjamsFeatures.Feature)
     */
    @Deprecated
    public void removeFeature(final NjamsFeatures.Feature feature) {
        njamsFeatures.remove(feature);
    }

    /**
     * @return if this client instance is started
     * @see #getNjamsState()
     * @see NjamsState#isStarted()
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
