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
import com.im.njams.sdk.argos.ArgosMetric;
import com.im.njams.sdk.argos.ArgosMultiCollector;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.communication.InstructionListener;
import com.im.njams.sdk.communication.ReplayHandler;
import com.im.njams.sdk.communication.Sender;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.image.ImageSupplier;
import com.im.njams.sdk.model.image.ResourceImageSupplier;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;
import com.im.njams.sdk.njams.NjamsArgos;
import com.im.njams.sdk.njams.configuration.NjamsConfiguration;
import com.im.njams.sdk.njams.NjamsFeatures;
import com.im.njams.sdk.njams.NjamsJobs;
import com.im.njams.sdk.njams.NjamsProjectMessage;
import com.im.njams.sdk.njams.communication.receiver.NjamsReceiver;
import com.im.njams.sdk.njams.communication.sender.NjamsSender;
import com.im.njams.sdk.njams.NjamsSettings;
import com.im.njams.sdk.njams.NjamsState;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.serializer.Serializer;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This is an instance of nJAMS. It cares about lifecycle and initializations
 * and holds references to the process models and global variables.
 */
public class Njams{

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Njams.class);

    private final NjamsSettings njamsSettings;
    private final NjamsArgos njamsArgos;
    private final NjamsMetadata njamsMetadata;
    private final NjamsState njamsState;
    private final NjamsFeatures njamsFeatures;
    private final NjamsJobs njamsJobs;
    private final NjamsSender njamsSender;
    private final NjamsConfiguration njamsConfiguration;
    private final NjamsProjectMessage njamsProjectMessage;
    private final NjamsReceiver njamsReceiver;

    /**
     * Create a nJAMS instance without a default client version.
     * It's initializing everything that is needed to communicate with the nJAMS Server
     * or the Argos agent and to produce appropriate messages.
     *
     * @param clientPath unique path per nJAMS instance.
     * @param category should describe the technology for the client that is used, e.g. BW5, BW6, MULE4EE
     * @param settings needed for client initialization of communication, sending intervals and sizes, etc.
     */
    public Njams(Path clientPath, String category, Settings settings) {
        this(new NjamsFactory(clientPath, category, settings));
    }
    /**
     * Create a nJAMS instance. It's initializing everything that is needed to communicate with the nJAMS Server
     * or the Argos agent and to produce appropriate messages.
     *
     * @param clientPath unique path per nJAMS instance.
     * @param defaultClientVersion  the default version of the nJAMS client instance if no client version can be found otherwise.
     * @param category should describe the technology for the client that is used, e.g. BW5, BW6, MULE4EE
     * @param settings needed for client initialization of communication, sending intervals and sizes, etc.
     */
    public Njams(Path clientPath, String defaultClientVersion, String category, Settings settings) {
        this(new NjamsFactory(clientPath, category, settings, defaultClientVersion));
    }

    /**
     * Creates a nJAMS instance that uses the given factory to retrieve all its necessary tools.
     *
     * @param njamsFactory the factory that is used to create all necessary tools
     */
    public Njams(NjamsFactory njamsFactory){
        njamsSettings = njamsFactory.getNjamsSettings();
        njamsMetadata = njamsFactory.getNjamsMetadata();
        njamsArgos = njamsFactory.getNjamsArgos();
        njamsState = njamsFactory.getNjamsState();
        njamsFeatures = njamsFactory.getNjamsFeatures();
        njamsJobs = njamsFactory.getNjamsJobs();
        njamsSender = njamsFactory.getNjamsSender();
        njamsConfiguration = njamsFactory.getNjamsConfiguration();
        njamsProjectMessage = njamsFactory.getNjamsProjectMessage();
        njamsReceiver = njamsFactory.getNjamsReceiver();

        printStartupBanner();
    }

    private void printStartupBanner() {
        Map<String, String> versions = njamsMetadata.getAllVersions();
        Map<String, String> settings = njamsSettings.getAllPropertiesWithoutPasswords();
        String currentYear = njamsMetadata.getCurrentYear();

        print(versions, settings, currentYear);
    }

    private void print(Map<String, String> versions, Map<String, String> settings, String currentYear) {
        String boundary = "************************************************************";
        String prefix = "***      ";
        LOG.info(boundary);
        printPrefixedCopyrightForCurrentYear(prefix, currentYear);
        LOG.info(prefix);
        LOG.info(prefix + "Version Info:");
        printPrefixed(prefix, versions);
        LOG.info(prefix);
        LOG.info(prefix + "Settings:");
        printPrefixed(prefix, settings);
        LOG.info(boundary);
    }

    private void printPrefixedCopyrightForCurrentYear(String prefix, String currentYear) {
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
     * Starts everything that is needed for communicating with the nJAMS server and processing messages.
     *
     * @return true if starting successful or it was already started
     */
    public boolean start() {
        if (!njamsState.isStarted()) {
            njamsReceiver.start();
            njamsJobs.start();
            njamsConfiguration.start();
            njamsProjectMessage.start();
            njamsState.start();
        }
        return njamsState.isStarted();
    }

    /**
     * Stops the instance with all its processing and releases the connections. It can't
     * be stopped before it started, otherwise it will throw a NjamsSdkRuntimeException
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
            njamsState.handleStopBeforeStart();
        }
        return njamsState.isStopped();
    }

//################################### Settings

    /**
     * The settings are used for different parameters like flush size, connection parameters etc.
     *
     * @return the settings for this njams instance.
     */
    public Settings getSettings() {
        return njamsSettings.getSettings();
    }

//################################### NjamsSender

    /**
     * Returns a Sender implementation, which is configured as specified in
     * the settings.
     *
     * @return the Sender that is used to send messages to the nJAMS server
     */
    @Deprecated
    public Sender getSender() {
        return njamsSender.getSender();
    }

//################################### NjamsArgos

    /**
     * Adds an Argos Collector that is used to create statistics, which will be sent periodically to the
     * njams agent that was specified in the settings.
     *
     * @param collector The collector that will collect statistics which will be sent to njams agent.
     * @param <T> The type of metric that will be created by the collector
     */
    @Deprecated
    public <T extends ArgosMetric> void addArgosCollector(ArgosMultiCollector<T> collector) {
        njamsArgos.addCollector(collector);
    }

    /**
     * Removes a previously set Argos Collector. By that, no more statistics from this collector will be sent to njams
     * agent.
     *
     * @param collector the Collector that will be removed
     * @param <T> The type of metric that will no longer be created by the collector
     */
    @Deprecated
    public <T extends ArgosMetric> void removeArgosCollector(ArgosMultiCollector<T> collector) {
        njamsArgos.remove(collector);
    }

//################################### NjamsJobs

    /**
     * The ReplayHandler is used to handle the replay command of the server.
     *
     * @return The handler that is used by this njams instance to handle replay command.
     */
    @Deprecated
    public ReplayHandler getReplayHandler() {
        return njamsJobs.getReplayHandler();
    }

    /**
     * Sets the ReplayHandler to process the replay of a job, which is triggered by the nJAMS server.
     *
     * @param replayHandler the handler to replay the job
     */
    @Deprecated
    public void setReplayHandler(final ReplayHandler replayHandler) {
        njamsJobs.setReplayHandler(replayHandler);
    }

    /**
     * Returns the job to the corresponding jobId.
     *
     * @param jobId the key to the corresponding job
     * @return the corresponding job
     */
    @Deprecated
    public Job getJobById(final String jobId) {
        return njamsJobs.get(jobId);
    }

    /**
     * Returns all jobs that are currently handled by this instance.
     *
     * @return A collections of all currently handled jobs
     */
    @Deprecated
    public Collection<Job> getJobs() {
        return njamsJobs.get();
    }

    /**
     * Adds a job to the jobs that are handled by this instance.
     *
     * @param job the job to add
     */
    @Deprecated
    public void addJob(Job job) {
        njamsJobs.add(job);
    }

    /**
     * Removes a job from this instance.
     *
     * @param jobId Key to remove the job from all the jobs.
     */
    @Deprecated
    public void removeJob(String jobId) {
        njamsJobs.remove(jobId);
    }


//################################### NjamsReceiver

    /**
     * Returns the instructions listeners that are used when a command of the njams server is sent to this client.
     * The instruction will be sent to each listener which can then decide to do with that instruction.
     *
     * @return the instructionListeners that are used for instruction handling.
     */
    public List<InstructionListener> getInstructionListeners() {
        return njamsReceiver.getInstructionListeners();
    }

    /**
     * Adds a new InstructionListener which will be called if a new Instruction
     * will be received.
     *
     * @param listener the new listener to be called
     */
    public void addInstructionListener(InstructionListener listener) {
        njamsReceiver.addInstructionListener(listener);
    }

    /**
     * Removes a InstructionListener. No new instructions from the server will be processed by this listener.
     *
     * @param listener the listener to remove
     */
    public void removeInstructionListener(InstructionListener listener) {
        njamsReceiver.removeInstructionListener(listener);
    }

//################################### NjamsSerializers

    /**
     * Adds a serializer which is used for serializing different objects to Strings that can be transferred to the
     * nJAMS server.
     *
     * @param key Class for which the serializer should be registered.
     * @param serializer A serializer that can serialize instances of class key.
     * @param <T> Type that the given instance serializes to Strings.
     *
     * @return If a serializer for the same type was already registered before,
     * the former registered serializer is returned. Otherwise <code>null</code> is returned.
     */
    @Deprecated
    public <T> Serializer<T> addSerializer(final Class<T> key, final Serializer<? super T> serializer) {
        return njamsProjectMessage.addSerializer(key, serializer);
    }

    /**
     * Removes a serializer. If an object of that class was serialized now, it would use a more generic serializer instead
     * to be serialized.
     *
     * @param key Class for which the serializer should be removed.
     * @param <T> Type that the given instance serializes to String.
     *
     * @return Removed serializer or <b>null</b>
     */
    @Deprecated
    public <T> Serializer<T> removeSerializer(final Class<T> key) {
        return njamsProjectMessage.removeSerializer(key);
    }

    /**
     * Gets the matching serializer for the given key.
     *
     * @param key Class of the serializer.
     * @param <T> Type that the given instance serializes to String.
     *
     * @return Registered serializer or <b>null</b>
     */
    @Deprecated
    public <T> Serializer<T> getSerializer(final Class<T> key) {
        return njamsProjectMessage.getSerializer(key);
    }

    /**
     * Serializes an object to a String with the best matching serializer.
     *
     * @param t Object to be serialized.
     * @param <T> Type of the Object that needs to be serialized.
     *
     * @return a string representation of the object.
     */
    @Deprecated
    public <T> String serialize(final T t) {
        return njamsProjectMessage.serialize(t);
    }

    /**
     * Gets the serializer with the given class key.
     * If not serializer is registered yet, the superclass hierarchy will be checked recursively.
     * If neither the class nor any superclass if registered, the interface
     * hierarchy will be checked recursively. if no (super) interface is
     * register, <b>null</b> will be returned.
     *
     * @param clazz Class for which a serializer should be found.
     * @param <T> Type that the given class serializes to String.
     *
     * @return The next best matching serializer or <b>null</b> if no serializer was found.
     */
    @Deprecated
    public <T> Serializer<? super T> findSerializer(final Class<T> clazz) {
        return njamsProjectMessage.findSerializer(clazz);
    }

//################################### Configuration

    /**
     * Returns the configuration that is used by this instance to determine the log level, trace points, etc.
     *
     * @return the configuration of this instance.
     */
    @Deprecated
    public Configuration getConfiguration() {
        return njamsConfiguration.getConfiguration();
    }

    /**
     * Returns the log mode of this instance.
     *
     * @return LogMode of this instance
     */
    @Deprecated
    public LogMode getLogMode() {
        return njamsConfiguration.getLogMode();
    }

//################################### NjamsMetadata

    /**
     * The category/technology of the instance.
     * It might be something like MULE4, BW5, etc.
     *
     * @return the category of the nJAMS client, which should describe the technology
     */
    @Deprecated
    public String getCategory() {
        return njamsMetadata.getCategory();
    }

    /**
     * The unique path or the root of this client.
     *
     * @return the root for this instance
     */
    @Deprecated
    public Path getClientPath() {
        return njamsMetadata.getClientPath();
    }

    /**
     * The client version that uses this sdk.
     *
     * @return the clientVersion that uses this sdk.
     */
    @Deprecated
    public String getClientVersion() {
        return njamsMetadata.getClientVersion();
    }

    /**
     * The current sdk version.
     *
     * @return the current sdkVersion
     */
    @Deprecated
    public String getSdkVersion() {
        return njamsMetadata.getSdkVersion();
    }

    /**
     * The name of the machine where the njams client is running on.
     *
     * @return the machine's name
     */
    @Deprecated
    public String getMachine() {
        return njamsMetadata.getMachine();
    }


//################################### NjamsFeatures

    /**
     * The features that this instance has.
     *
     * @return the list of features this instance has.
     */
    @Deprecated
    public List<String> getFeatures() {
        return njamsFeatures.get();
    }

    /**
     * Add a feature that this instance has.
     *
     * @param feature to add
     */
    @Deprecated
    public void addFeature(NjamsFeatures.Feature feature) {
        njamsFeatures.add(feature);
    }

    /**
     * Remove a feature that this instance doesn't have
     *
     * @param feature to remove
     */
    @Deprecated
    public void removeFeature(final NjamsFeatures.Feature feature) {
        njamsFeatures.remove(feature);
    }

//################################### NjamsState

    /**
     * Returns if this instance is currently running.
     *
     * @return true if start was called and no stop after that. It is false, if stop was called after start or if
     * start has never been called.
     */
    @Deprecated
    public boolean isStarted() {
        return njamsState.isStarted();
    }

//################################### NjamsProjectMessage

    /**
     * Returns all the global variables for this instance.
     *
     * @return the globalVariables for this instance.
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
     * @param key the key of the image
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
     *
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
     *
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
     * Set the type for a TreeElement given by a path.
     *
     * @param path the path of the tree icon
     * @param type icon type of the tree element
     */
    @Deprecated
    public void setTreeElementType(Path path, String type) {
        njamsProjectMessage.setTreeElementType(path, type);
    }

    /**
     * Gets the ProcessModelLayouter that is used to create a visual graph for the nJAMS server from the ProcessModels.
     * @return the processModelLayouter that is currently used for layouting.
     */
    @Deprecated
    public ProcessModelLayouter getProcessModelLayouter() {
        return njamsProjectMessage.getProcessModelLayouter();
    }

    /**
     * Set the ProcessModelLayouter that is used to create a visual graph for the nJAMS server from the ProcessModels.
     *
     * @param processModelLayouter the processModelLayouter to use for layouting.
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
     * Gets the ProcessDiagramFactory that is used to create an image that can be sent to nJAMS server.
     *
     * @return the ProcessDiagramFactory that is used to create images.
     */
    @Deprecated
    public ProcessDiagramFactory getProcessDiagramFactory() {
        return njamsProjectMessage.getProcessDiagramFactory();
    }

    /**
     * Sets the ProcessDiagramFactory that is used to create an image that can be sent to nJAMS server.
     *
     * @param processDiagramFactory the processDiagramFactory that will be used to create images.
     */
    @Deprecated
    public void setProcessDiagramFactory(ProcessDiagramFactory processDiagramFactory) {
        njamsProjectMessage.setProcessDiagramFactory(processDiagramFactory);
    }
}
