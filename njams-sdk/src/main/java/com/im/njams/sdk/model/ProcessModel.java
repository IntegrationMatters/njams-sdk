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
package com.im.njams.sdk.model;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.im.njams.sdk.NjamsMetadata;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.model.layout.ProcessModelLayouter;
import com.im.njams.sdk.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.IdUtil;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.logmessage.JobImpl;
import com.im.njams.sdk.model.svg.ProcessDiagramFactory;

/**
 * A ProcessModel represents one process/flow in engine to monitor.
 *
 * Instances of running jobs for the process are managed here.
 *
 * @author pnientiedt
 */
public class ProcessModel {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessModel.class);

    // Currently known ActivityModels mapped by modelId
    private final Map<String, ActivityModel> activities = new LinkedHashMap<>();
    private final Map<String, ActivityModel> startActivities = new LinkedHashMap<>();
    private final Njams njams;

    // Currently known TransitionModels mapped by modelId
    private final Map<String, TransitionModel> transitions = new LinkedHashMap<>();
    private final Configuration configuration;
    private final ProcessModelLayouter processModelLayouter;
    private final ProcessDiagramFactory processDiagramFactory;
    private final NjamsMetadata instanceMetaData;
    private final Settings settings;

    private boolean starter;

    private final Path path;

    private String svg;

    // internal properties, shall no go to project message
    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * Create a new ProcessModel to Path and connect it to the given Njams
     *
     * @param path Path for this ProcessModel
     * @param njams Njams for this ProcessModel
     */
    public ProcessModel(final Path path, final Njams njams) {
        this.path = path;
        this.njams = njams;
        this.configuration = njams.getConfiguration();
        this.processModelLayouter = njams.getProcessModelLayouter();
        this.processDiagramFactory = njams.getProcessDiagramFactory();
        this.instanceMetaData = njams.getNjamsMetadata();
        this.settings = njams.getSettings();
    }

    /**
     * Create a messageformat representation of this ProcessModel, which can be
     * serialized and send to the server
     *
     * @return messageformat representation of this ProcessModel
     */
    public com.faizsiegeln.njams.messageformat.v4.projectmessage.ProcessModel getSerializableProcessModel() {
        com.faizsiegeln.njams.messageformat.v4.projectmessage.ProcessModel internalProcessModel =
                new com.faizsiegeln.njams.messageformat.v4.projectmessage.ProcessModel();

        // set meta data
        internalProcessModel.setPath(path.toString());
        internalProcessModel.setName(path.getObjectName());

        // set configuration data
        ProcessConfiguration processConfiguration = configuration.getProcess(path.toString());
        if (processConfiguration != null) {
            internalProcessModel.setLogLevel(processConfiguration.getLogLevel());
            internalProcessModel.setExclude(processConfiguration.isExclude());
        }

        // copy activities
        activities.values().stream()
                .map(a -> a.getSerializableActivity(processConfiguration))
                .forEach(activity -> internalProcessModel.getActivities().add(activity));

        // copy transitions
        transitions.values().stream()
                .map(TransitionModel::getSerializableTransition)
                .forEach(transition -> internalProcessModel.getTransitions().add(transition));

        try {
            // process SVG
            if (svg == null) {
                // create process layout
                processModelLayouter.layout(this);
                // build SVG
                svg = processDiagramFactory.getProcessDiagram(this);
            }
            internalProcessModel.setSvg(svg);
            internalProcessModel.setSvgStatus(ProcessDiagramFactory.SUCCESS_STATUS);
        } catch (Exception e) {
            LOG.error("Error creating SVG", e);
            internalProcessModel.setSvg(null);
            internalProcessModel.setSvgStatus(e.getMessage());
        }

        return internalProcessModel;
    }

    /**
     *
     * @return Njams
     */
    public Njams getNjams() {
        return njams;
    }


    public String getCategory(){
        return instanceMetaData.category;
    }

    /**
     *
     * @return Path of this ProcessModel
     */
    public Path getPath() {
        return path;
    }

    /**
     *
     * @return Name of this ProcessModel
     */
    public String getName() {
        return path.getObjectName();
    }

    /**
     * Returns a list of all activities of this process. This includes groups,
     * too.
     *
     * @return the list of {@link ActivityModel}
     */
    public List<ActivityModel> getActivityModels() {
        return Collections.unmodifiableList(new ArrayList<>(activities.values()));
    }

    /**
     * Returns a list of all groups of this process.
     *
     * @return the list of {@link GroupModel}
     */
    public List<GroupModel> getGroupModels() {
        return activities.values().stream()
                .filter(activity -> GroupModel.class.isAssignableFrom(activity.getClass())).map(GroupModel.class::cast)
                .collect(toList());
    }

    /**
     * Returns a list of all subProcesses of this process.
     *
     * @return the list of {@link SubProcessActivityModel}
     */
    public List<SubProcessActivityModel> getSubProcessModels() {
        return activities.values().stream()
                .filter(activity -> SubProcessActivityModel.class.isAssignableFrom(activity.getClass()))
                .map(SubProcessActivityModel.class::cast)
                .collect(toList());
    }

    /**
     * Return the Models for drawing.
     *
     * @return the list of {@link TransitionModel}
     */
    public List<TransitionModel> getTransitionModels() {
        return Collections.unmodifiableList(new ArrayList<>(transitions.values()));
    }

    /**
     * Add a new ActivityModel to the process.
     *
     * @param activityModel to add
     */
    public void addActivity(ActivityModel activityModel) {
        String id = activityModel.getId();
        if (activities.containsKey(id) && activities.get(id) != activityModel) {
            throw new NjamsSdkRuntimeException(
                    "ProcessModel " + getPath() + " already contains a ActivityModel with id " + id + "!");
        }
        activities.put(id, activityModel);
    }

    /**
     * Retrieve a ActivityModel by its modelId.
     *
     * @param activityModelId to search for
     * @return the requested ActivityModel or an Exception
     */
    public ActivityModel getActivity(String activityModelId) {
        return activities.get(activityModelId);
    }

    /**
     * Retrieve a GroupModel by its modelId.
     *
     * @param groupModelId to search for
     * @return the requested ActivityModel or an Exception
     */
    public GroupModel getGroup(final String groupModelId) {
        final ActivityModel activityModel = activities.get(groupModelId);
        if (activityModel != null && !(activityModel instanceof GroupModel)) {
            throw new NjamsSdkRuntimeException(
                    "ActivityModel with id " + groupModelId + " found, but GroupModel expected!");
        }
        return (GroupModel) activityModel;
    }

    /**
     * Retrieve a SubProcessModel by its modelId.
     *
     * @param subProcessModelId to search for
     * @return the requested ActivityModel or an Exception
     */
    public SubProcessActivityModel getSubProcess(final String subProcessModelId) {
        final ActivityModel activityModel = activities.get(subProcessModelId);
        if (activityModel != null && !(activityModel instanceof SubProcessActivityModel)) {
            throw new NjamsSdkRuntimeException(
                    "SubProcessModel with id " + subProcessModelId + " found, but SubProcessModel expected!");
        }
        return (SubProcessActivityModel) activityModel;
    }

    /**
     * Add a new TransitionModel to the process.
     *
     * @param transitionModel to add
     */
    public void addTransition(TransitionModel transitionModel) {
        String id = transitionModel.getId();
        if (transitions.containsKey(id) && transitions.get(id) != transitionModel) {
            throw new NjamsSdkRuntimeException(
                    "ProcessModel " + getPath() + " already contains a TransitionModel with id " + id + "!");
        }
        transitions.put(id, transitionModel);
    }

    /**
     * Retrieve a TransitionModel by its modelId.
     *
     * @param transitionModelId * to search for
     * @return the requested TransitionModel or an Exception
     */
    public TransitionModel getTransition(String transitionModelId) {
        return transitions.get(transitionModelId);
    }

    /**
     * Create a new {@link ActivityModel} in this {@link ProcessModel}.
     *
     * @param startActivityModelId * of Actvity to create
     * @param startActivityName of Actvity to create
     * @param startActivityType of Actvity to create
     * @return the created {@link ActivityModel}
     */
    public ActivityModel createActivity(String startActivityModelId, String startActivityName,
            String startActivityType) {
        ActivityModel activity = new ActivityModel(this);
        activity.setId(startActivityModelId);
        activity.setName(startActivityName);
        activity.setType(startActivityType);
        addActivity(activity);
        return activity;
    }

    /**
     * Create a new {@link GroupModel} in this {@link ProcessModel}.
     *
     * @param groupModelId * of Actvity to create
     * @param groupName of Actvity to create
     * @param groupType of Actvity to create
     * @return the created {@link ActivityModel}
     */
    public GroupModel createGroup(String groupModelId, String groupName, String groupType) {
        GroupModel group = new GroupModel(this);
        group.setId(groupModelId);
        group.setName(groupName);
        group.setType(groupType);
        addActivity(group);
        return group;
    }

    /**
     * Create a new {@link SubProcessActivityModel} in this
     * {@link ProcessModel}.
     *
     * @param subProcessModelId * of Actvity to create
     * @param subProcessName of Actvity to create
     * @param subProcessType of Actvity to create
     * @return the created {@link ActivityModel}
     */
    public SubProcessActivityModel createSubProcess(String subProcessModelId, String subProcessName,
            String subProcessType) {
        SubProcessActivityModel subProcessModel = new SubProcessActivityModel(this);
        subProcessModel.setId(subProcessModelId);
        subProcessModel.setName(subProcessName);
        subProcessModel.setType(subProcessType);
        addActivity(subProcessModel);
        return subProcessModel;
    }

    /**
     * Create a new {@link TransitionModel} in this {@link ProcessModel} between
     * the activities with the given IDs.
     *
     * The transitionModelId will be calculated.
     *
     * @param fromActivityModelId The ID of the transition's start activity.
     * @param toActivityModelId The ID of the transition's start activity.
     * @return the created {@link TransitionModel}
     */
    public TransitionModel createTransition(String fromActivityModelId, String toActivityModelId) {
        return createTransition(fromActivityModelId, toActivityModelId,
                IdUtil.getTransitionModelId(fromActivityModelId, toActivityModelId));
    }

    /**
     * Create a new {@link TransitionModel} in this {@link ProcessModel} between
     * the activities with the given IDs.
     *
     * The transitionModelId is given from outside.
     *
     * @param fromActivityModelId The ID of the transition's start activity.
     * @param toActivityModelId The ID of the transition's start activity.
     * @param transitionModelId The ID of the new {@link TransitionModel}.
     * @return the created {@link TransitionModel}
     */
    public TransitionModel createTransition(String fromActivityModelId, String toActivityModelId,
            String transitionModelId) {
        ActivityModel fromActivityModel = activities.get(fromActivityModelId);
        if (fromActivityModel == null) {
            throw new NjamsSdkRuntimeException("FromActivityModel with id " + fromActivityModelId + " does not exist");
        }
        ActivityModel toActivityModel = activities.get(toActivityModelId);
        if (toActivityModel == null) {
            throw new NjamsSdkRuntimeException("ToActivityModel with id " + toActivityModelId + " does not exist");
        }
        TransitionModel transition = new TransitionModel(this, transitionModelId, transitionModelId);
        transition.setFromActivity(fromActivityModel);
        transition.setToActivity(toActivityModel);
        addTransition(transition);
        return transition;
    }

    /**
     * Creates a job and generates a new logId and the same jobId for it.
     *
     * @return the {@link Job}
     */
    public Job createJob() {
        String logId = createUniqueLogId();
        return createJobWithExplicitLogId(logId, logId);
    }

    /**
     * Creates a job with a given external jobId from Engine.
     *
     * @param jobId the external jobId in Engine
     * @return the {@link Job}
     */
    public Job createJob(String jobId) {
        return createJobWithExplicitLogId(jobId, createUniqueLogId());
    }

    /**
     * This method returns a unique log id.
     *
     * @return UUID as String
     */
    private String createUniqueLogId(){
        return IdUtil.createLogId();
    }

    /**
     * <strong>Don't use this method if you don't have to!<br>
     * Use either {@link #createJob() createJob()} or {@link #createJob(String) createJob(String jobId)}.</strong><br>
     *
     * Creates a job with a given external jobId from Engine and with an explicitly
     * set logId! This should only be used if you know what you are doing, because the
     * logId normally is a UUID. E.g. this is a necessary method for the replay plugin,
     * because it has to use the same logId as the job that is replayed.
     * @param jobId the external jobId in Engine
     * @param logId the explicitly set logId
     * @return the {@link Job}
     */
    public Job createJobWithExplicitLogId(String jobId, String logId){
        Job job = new JobImpl(this, jobId, logId);
        njams.addJob(job);
        return job;
    }

    /**
     * @return the starter
     */
    public boolean isStarter() {
        return starter;
    }

    /**
     * @param starter the starter to set
     */
    public void setStarter(boolean starter) {
        this.starter = starter;
    }

    /**
     *
     * @param svg the svg to set
     */
    public void setSvg(String svg) {
        this.svg = svg;
    }

    /**
     * Returns a list of all started Activities
     *
     * @return List of started Activities
     */
    public List<ActivityModel> getStartActivities() {
        return Collections.unmodifiableList(new ArrayList<>(startActivities.values()));
    }

    /**
     *
     * @param activity Add started activity
     */
    public void addStartActivity(ActivityModel activity) {
        if (!activity.isStarter()) {
            activity.setStarter(true);
        }
        startActivities.put(activity.getId(), activity);
    }

    /**
     *
     * @param activity remove started activity
     */
    public void removeStartActivity(ActivityModel activity) {
        if (activity.isStarter()) {
            activity.setStarter(false);
        }
        startActivities.remove(activity.getId());
    }

    @Override
    public String toString() {
        return "ProcessModel{" + "path=" + path + '}';
    }

    /**
     * Gets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @return Properties value of <b>null</b>
     */
    public Object getProperty(final String key) {
        return properties.get(key);
    }

    /**
     * Checks whether the activity has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     */
    public boolean hasProperty(final String key) {
        return properties.containsKey(key);
    }

    /**
     * Sets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @param value value of the property
     */
    public void setProperty(final String key, final Object value) {
        properties.put(key, value);
    }

    public boolean shouldDeprecatedPathFieldForSubprocessesBeUsed() {
        final String useDeprecatedPathFieldForSubprocesses = settings
            .getProperty(Settings.PROPERTY_USE_DEPRECATED_PATH_FIELD_FOR_SUBPROCESSES);
        return useDeprecatedPathFieldForSubprocesses != null && "true".equalsIgnoreCase(useDeprecatedPathFieldForSubprocesses);
    }
}
