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
package com.im.njams.sdk;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.argos.NjamsArgos;
import com.im.njams.sdk.client.NjamsMetadata;
import com.im.njams.sdk.client.NjamsMetadataFactory;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.NjamsFeatures;
import com.im.njams.sdk.logmessage.NjamsJobs;
import com.im.njams.sdk.logmessage.NjamsProjectMessage;
import com.im.njams.sdk.logmessage.NjamsState;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.serializer.NjamsSerializers;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.im.njams.sdk.logmessage.NjamsState.NOT_STARTED_EXCEPTION_MESSAGE;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 */
public class Njams{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);
    private final NjamsMetadata njamsMetadata;
    private final NjamsSerializers njamsSerializers;
    private final NjamsJobs njamsJobs;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;

    // The settings of the client
    private final Settings njamsSettings;

    private final NjamsProjectMessage njamsProjectMessage;
    private final NjamsVersions njamsVersions;
    private final String currentYear;
    private final NjamsArgos njamsArgos;
    private final NjamsReceiver njamsReceiver;

    private final NjamsConfiguration njamsConfiguration;
    private final com.im.njams.sdk.NjamsSender njamsSender;

    /**
     * Create a nJAMS client.
     *
     * @param path     the path in the tree
     * @param defaultClientVersion  the version of the nNJAMS client
     * @param category the category of the nJAMS client, should describe the
     *                 technology
     * @param settings needed settings for client eg. for communication
     */
    public Njams(Path path, String defaultClientVersion, String category, Settings settings) {
        njamsSettings = settings;

        njamsArgos = new NjamsArgos(settings);

        Map<String, String> classpathVersions = readNjamsVersionsFiles();
        final String CURRENT_YEAR = "currentYear";
        currentYear = classpathVersions.remove(CURRENT_YEAR);
        njamsVersions = fillNjamsVersions(classpathVersions, defaultClientVersion);

        njamsMetadata = NjamsMetadataFactory.createMetadataFor(path, njamsVersions.getClientVersion(),
            njamsVersions.getSdkVersion(), category);

        njamsSerializers = new NjamsSerializers();

        njamsState = new NjamsState();

        njamsFeatures = new NjamsFeatures();

        njamsJobs = new NjamsJobs(njamsMetadata, njamsState, njamsFeatures, settings);

        njamsSender = NjamsSenderFactory.getNjamsSender(njamsSettings, njamsMetadata);

        njamsConfiguration = NjamsConfigurationFactory.getNjamsConfiguration(njamsMetadata, njamsSender, settings);

        njamsProjectMessage = new NjamsProjectMessage(njamsMetadata, njamsFeatures, njamsConfiguration,
            njamsSender, njamsState, njamsJobs, njamsSerializers, njamsSettings);

        njamsReceiver = new NjamsReceiver(njamsSettings, njamsMetadata, njamsFeatures, njamsProjectMessage, njamsJobs,
            njamsConfiguration);

        printStartupBanner();
    }

    /**
     * Read the versions from njams.version files. Set the SDK-Version and the
     * Client-Version if found.
     */
    private Map<String, String> readNjamsVersionsFiles() {
        try {
            return readVersionsFromFilesWithName("njams.version");
        } catch (Exception e) {
            return handleFileError(e);
        }
    }

    private Map<String, String> readVersionsFromFilesWithName(String fileName) throws IOException {
        Map<String, String> versions = new HashMap<>();
        final Enumeration<URL> urls = getAllUrlsForFileWithName(fileName);
        while (urls.hasMoreElements()) {
            final URL url = urls.nextElement();
            Map<String, String> propertiesOfOneFile = readAllPropertiesFromFileWithURL(url);
            versions.putAll(propertiesOfOneFile);
        }
        return versions;
    }

    private Map<String, String> readAllPropertiesFromFileWithURL(URL url) throws IOException {
        Map<String, String> keyValuePairs = new HashMap<>();
        final Properties prop = new Properties();
        prop.load(url.openStream());
        prop.forEach((key, value) -> keyValuePairs.put(String.valueOf(key), String.valueOf(value)));
        return keyValuePairs;
    }

    private Enumeration<URL> getAllUrlsForFileWithName(String fileName) throws IOException {
        return this.getClass().getClassLoader().getResources(fileName);
    }

    private Map<String, String> handleFileError(Exception e) {
        LOG.error("Unable to load versions from njams.version files", e);
        return new HashMap<>();
    }

    private NjamsVersions fillNjamsVersions(Map<String, String> classpathVersions, String defaultClientVersion) {
        final String sdkDefaultVersion = "4.0.0.alpha";
        return new NjamsVersions(classpathVersions).
            setSdkVersionIfAbsent(sdkDefaultVersion).
            setClientVersionIfAbsent(defaultClientVersion);
    }

    private void printStartupBanner() {
        Map<String, String> versions = njamsVersions.getAllVersions();
        Map<String, String> settings = njamsSettings.getAllPropertiesWithoutPasswords();

        print(versions, settings);
    }

    private void print(Map<String, String> versions, Map<String, String> settings) {
        String boundary = "************************************************************";
        String prefix = "***      ";
        LOG.info(boundary);
        printPrefixedCopyrightForCurrentYear(prefix);
        LOG.info(prefix);
        LOG.info(prefix + "Version Info:");
        printPrefixed(prefix, versions);
        LOG.info(prefix);
        LOG.info(prefix + "Settings:");
        printPrefixed(prefix, settings);
        LOG.info(boundary);
    }

    private void printPrefixedCopyrightForCurrentYear(String prefix) {
        LOG.info(prefix + ("nJAMS SDK: Copyright (c) " + currentYear + " Faiz & Siegeln Software GmbH"));
    }

    private void printPrefixed(String prefix, Map<String, String> map) {
        map.
            entrySet().
            stream().
            sorted(Map.Entry.comparingByKey()).
            forEach(v -> LOG.info(prefix + v.getKey() + ": " + v.getValue()));
    }

    /**
     * @return the current nJAMS settings
     */
    public Settings getSettings() {
        return njamsSettings;
    }


    /**
     * Start a client; it will initiate the connections and start processing.
     *
     * @return true if successful
     */
    public boolean start() {
        if (!njamsState.isStarted()) {
            if (njamsSettings == null) {
                throw new NjamsSdkRuntimeException("Settings not set");
            }
            njamsReceiver.start();
            njamsJobs.start();
            njamsConfiguration.start();
            njamsState.start();
            njamsProjectMessage.sendProjectMessage();
        }
        return njamsState.isStarted();
    }



    /**
     * Stop a client; it stops processing and release the connections. It can't
     * be stopped before it started. (NjamsSdkRuntimeException)
     *
     * @return true is stopping was successful.
     */
    public boolean stop() {
        if (njamsState.isStarted()) {
            njamsJobs.stop();
            njamsConfiguration.stop();
            njamsArgos.stop();
            njamsSender.stop();
            njamsReceiver.stop();
            njamsState.stop();
        } else {
            throw new NjamsSdkRuntimeException(NOT_STARTED_EXCEPTION_MESSAGE);
        }
        return wasStoppingSuccessful();
    }

    private boolean wasStoppingSuccessful() {
        return njamsState.isStopped();
    }

//################################### NjamsSender

    public com.im.njams.sdk.NjamsSender getNjamsSender() {
        return njamsSender;
    }

    /**
     * Returns a Sender implementation, which is configured as specified in
     * the settings.
     *
     * @return the Sender
     */
    @Deprecated
    public Sender getSender() {
        return njamsSender.getSender();
    }

//################################### NjamsArgos

    /**
     * Adds a collector that will create statistics.
     *
     * @param collector The collector that collects statistics
     */
    @Deprecated
    public void addArgosCollector(ArgosMultiCollector collector) {
        njamsArgos.addCollector(collector);
    }

    @Deprecated
    public void removeArgosCollector(ArgosMultiCollector collector) {
        njamsArgos.remove(collector);
    }

//################################### NjamsJobs

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


//################################### NjamsReceiver

    /**
     * @return the instructionListeners
     */
    @Deprecated
    public List<InstructionListener> getInstructionListeners() {
        return njamsReceiver.getNjamsInstructionListeners().get();
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    @Deprecated
    public void addInstructionListener(InstructionListener listener) {
        njamsReceiver.getNjamsInstructionListeners().add(listener);
    }

    /**
     * Removes a InstructionListener from the Receiver.
     *
     * @param listener the listener to remove
     */
    @Deprecated
    public void removeInstructionListener(InstructionListener listener) {
        njamsReceiver.getNjamsInstructionListeners().remove(listener);
    }


//################################### NjamsSerializers

    public NjamsSerializers getNjamsSerializers(){
    return njamsSerializers;
}

    /**
     *
     * @param <T>        Type that the given instance serializes
     * @param key        Class for which the serializer should be registered
     * @param serializer A serializer that can serialize instances of class key
     *                   to Strings.
     * @return If a serializer for the same type was already registered before,
     * the former registered serializer is returned. Otherwise <code>null</code> is returned.
     *
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#add(Class, Serializer)
     */
    @Deprecated
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        return njamsSerializers.add(key, serializer);
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
        return njamsSerializers.remove(key);
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
        return njamsSerializers.get(key);
    }

    /**
     * @param <T> type of the class
     * @param t   Object to be serialized.
     * @return a string representation of the object.
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#serialize(Object)
     */
    @Deprecated
    public <T> String serialize(final T t) {
        return njamsSerializers.serialize(t);
    }

    /**
     * @param <T>   Type of the class
     * @param clazz Class for which a serializer will be searched.
     * @return Serizalizer of <b>null</b>.
     * @see #getNjamsSerializers() ()
     * @see NjamsSerializers#find(Class)
     */
    @Deprecated
    public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
        return njamsSerializers.find(clazz);
    }

//################################### Configuration

    public NjamsConfiguration getNjamsConfiguration() {
        return njamsConfiguration;
    }

    /**
     * @return the configuration
     */
    @Deprecated
    public Configuration getConfiguration() {
        return njamsConfiguration.getConfiguration();
    }

    /**
     * @return LogMode of this client
     */
    @Deprecated
    public LogMode getLogMode() {
        return njamsConfiguration.getLogMode();
    }

//################################### NjamsMetadata

    public NjamsMetadata getNjamsMetadata(){
        return njamsMetadata;
    }

    /**
     * @return the category of the nJAMS client, which should describe the
     * technology
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#category
     */
    @Deprecated
    public String getCategory() {
        return njamsMetadata.category;
    }

    /**
     * @return the clientPath
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#clientPath
     */
    @Deprecated
    public Path getClientPath() {
        return njamsMetadata.clientPath;
    }

    /**
     * @return the clientVersion
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#clientVersion
     */
    @Deprecated
    public String getClientVersion() {
        return njamsMetadata.clientVersion;
    }

    /**
     * @return the sdkVersion
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#sdkVersion
     */
    @Deprecated
    public String getSdkVersion() {
        return njamsMetadata.sdkVersion;
    }

    /**
     * @return the machine name
     * @see #getNjamsMetadata()
     * @see NjamsMetadata#machine
     */
    @Deprecated
    public String getMachine() {
        return njamsMetadata.machine;
    }


//################################### NjamsFeatures

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

//################################### NjamsState

    public NjamsState getNjamsState(){
        return njamsState;
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

//################################### NjamsProjectMessage

    public NjamsProjectMessage getNjamsProjectMessage(){
        return njamsProjectMessage;
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
     * Adds an image for a given resource path.
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

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    @Deprecated
    public boolean isExcluded(Path processPath) {
        return njamsProjectMessage.isExcluded(processPath);
    }

    /**
     * @return the ProcessDiagramFactory
     */
    @Deprecated
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return njamsProjectMessage.getProcessDiagramFactory();
    }

    /**
     * @param processDiagramFactory the processDiagramFactory to set
     */
    @Deprecated
    public void setProcessDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        njamsProjectMessage.setProcessDiagramFactory(processDiagramFactory);
    }
}
