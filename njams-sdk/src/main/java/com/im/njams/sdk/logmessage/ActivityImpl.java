/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.logmessage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.annotation.XmlTransient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;
import com.im.njams.sdk.logmessage.ExtractHandler.ExtractSource;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.model.TransitionModel;

/**
 * This is internal implementation of the Activity. It is a extension of the
 * MesasgeFormat activity, and provided functionality for easy chaining
 * activities.
 *
 * @author pnientiedt
 * @version 4.0.6
 */
public class ActivityImpl extends com.faizsiegeln.njams.messageformat.v4.logmessage.Activity implements Activity {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityImpl.class);

    //This lock is used for synchronizing the access to the activities attributes
    private final Object attributesLock = new Object();

    // Job will be hold because it will be needed when creating the next activity via stepTo logic
    private final JobImpl job;
    private final ActivityModel activityModel;
    private final Extract extract;
    private boolean starter = false;
    private GroupImpl parent = null;
    private boolean trace = false;
    private LocalDateTime startTime = DateTimeUtility.now();

    private long estimatedSize = 700L;
    private boolean ended = false;
    private boolean inputProcessecd = false;
    private boolean outputProcessed = false;

    /**
     * @deprecated SDK-140
     * @param job The job
     * @param modelId The model Id
     */
    @Deprecated
    public ActivityImpl(JobImpl job, String modelId) {
        this.job = job;
        setModelId(modelId);
        activityModel = null;
        extract = null;
    }

    public ActivityImpl(JobImpl job, ActivityModel model) {
        this.job = job;
        activityModel = Objects.requireNonNull(model);
        setModelId(model.getId());
        ActivityConfiguration activityConfig = job.getActivityConfiguration(activityModel);
        if (activityConfig != null) {
            extract = activityConfig.getExtract();
        } else {
            extract = null;
        }
    }

    /**
     * Returns the job where Activity is located. Must be transient so that it
     * will not be printed out during serialization time.
     *
     * @return the {@link Job}
     */
    @XmlTransient
    public Job getJob() {
        return job;
    }

    /**
     * Add a new Predecessor by given predecessorInstanceId and
     * transitionModelId
     *
     * @param predecessorInstanceId predecessorInstanceId to add
     * @param transitionModelId transitionModelId to add
     */
    @Override
    public void addPredecessor(String predecessorInstanceId, String transitionModelId) {
        getPredecessors().add(new Predecessor(transitionModelId, predecessorInstanceId));
    }

    /**
     * Step to a new Activity with a given toActivityModelId.
     * @deprecated SDK-140 Does not work for sub-processes.
     * @param toActivityModelId to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
    @Deprecated
    public ActivityBuilder stepTo(String toActivityModelId) {
        end();
        ActivityBuilder builder = new ActivityBuilder(job, toActivityModelId);
        builder.stepFrom(this);
        return builder;
    }

    /**
     * Step to a new Activity with a given toActivityModel.
     *
     * @param toActivityModel * to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
    public ActivityBuilder stepTo(ActivityModel toActivityModel) {
        if (toActivityModel instanceof GroupModel) {
            return stepToGroup((GroupModel) toActivityModel);
        }
        if (toActivityModel instanceof SubProcessActivityModel) {
            return stepToSubProcess((SubProcessActivityModel) toActivityModel);
        }
        TransitionModel transitionModel = toActivityModel.getIncomingTransitionFrom(getModelId());
        if (transitionModel == null) {
            throw new NjamsSdkRuntimeException("No transition from " + getModelId() + " to " + toActivityModel.getId()
                    + " found!");
        }
        end();
        //check if a activity with the same modelId and the same iteration and parent already exists.
        final ActivityImpl toActivity = (ActivityImpl) job.getActivityByModelId(toActivityModel.getId());
        final ActivityBuilder builder;
        if (toActivity == null || !Objects.equals(toActivity.getIteration(), getIteration())
                || toActivity.getParent() != getParent()) {
            builder = new ActivityBuilder(job, toActivityModel);
        } else {
            builder = new ActivityBuilder(toActivity);
        }
        builder.stepFrom(this, transitionModel);
        return builder;
    }

    void setStarter(final boolean starter) {
        this.starter = starter;
    }

    /**
     * Return if this ActivityImpl is a starter activity
     *
     * @return true if it is a starter activity
     */
    @Override
    @XmlTransient
    public boolean isStarter() {
        return starter;
    }

    /**
     * @return the parent
     */
    @Override
    @XmlTransient
    public GroupImpl getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(GroupImpl parent) {
        this.parent = parent;
    }

    /**
     * Step to a new Group with a given toGroupModelId.
     * @deprecated SDK-140 Does not work for sub-processes.
     * @param toGroupModelId to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
    @Deprecated
    public GroupBuilder stepToGroup(String toGroupModelId) {
        end();
        GroupBuilder builder = new GroupBuilder(job, toGroupModelId);
        builder.stepFrom(this);
        return builder;
    }

    /**
     * Step to a new Group with a given toGroupModel.
     *
     * @param toGroupModel to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
    public GroupBuilder stepToGroup(GroupModel toGroupModel) {
        end();
        TransitionModel transitionModel = toGroupModel.getIncomingTransitionFrom(getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final GroupImpl toGroup = (GroupImpl) job.getActivityByModelId(toGroupModel.getId());
        final GroupBuilder builder;
        if (toGroup == null || !Objects.equals(toGroup.getIteration(), getIteration())
                || toGroup.getParent() != getParent()) {
            builder = new GroupBuilder(job, toGroupModel);
        } else {
            builder = new GroupBuilder(toGroup);
        }
        builder.stepFrom(this, transitionModel);
        return builder;
    }

    /**
     * Returns the event associated with this activity. If the activity does not
     * have an event, one will be created.
     *
     * @return The event of the activity
     */
    @Override
    public Event createEvent() {
        return new Event(this);
    }

    /**
     * Start this activity
     */
    public void start() {
        startTime = DateTimeUtility.now();
        if (getActivityStatus() == null) {
            setActivityStatus(ActivityStatus.RUNNING);
        }
    }

    @XmlTransient
    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * Process the input for this activity. Checks for tracepoints, extracts and
     * similar functionality to decide if and how this input data will be
     * handled.
     *
     * @param input input data
     */
    @Override
    public void processInput(Object input) {
        if (input != null) {
            handleTracing(input, true);
            if (getInput() != null) {
                addToEstimatedSize(getInput().length());
                job.addToEstimatedSize(getInput().length());
            }
        }

        if (extract != null) {
            ExtractHandler.handleExtract(job, extract, this, ExtractSource.INPUT, input, getInput());
        } else if (activityModel == null) {
            // deprecated SDK-140
            ExtractHandler.handleExtract(job, this, ExtractSource.INPUT, input, getInput());
        }
        inputProcessecd = true;
    }

    /**
     * End this activity
     */
    @Override
    public void end() {
        if (ended) {
            return;
        }
        setDuration(Duration.between(startTime, DateTimeUtility.now()).toMillis());
        if (getActivityStatus() == ActivityStatus.RUNNING) {
            setActivityStatus(ActivityStatus.SUCCESS);
        }
        if (this instanceof GroupImpl) {
            ((GroupImpl) this).getChildActivities().stream()
                    .filter(a -> a.getActivityStatus() == ActivityStatus.RUNNING).forEach(a -> a.end());
        }
        //process input and output if not done yet, for extract rules which do not need data
        if (!inputProcessecd) {
            processInput(null);
        }
        if (!outputProcessed) {
            processOutput(null);
        }
        ended = true;
    }

    /**
     * Process the output for this activity. Checks for tracepoints, extracts
     * and similar functionality to decide if and how this output data will be
     * handled.
     *
     * @param output output data
     */
    @Override
    public void processOutput(Object output) {
        if (output != null) {
            handleTracing(output, false);
            if (getOutput() != null) {
                addToEstimatedSize(getOutput().length());
                job.addToEstimatedSize(getOutput().length());
            }
        }
        if (extract != null) {
            ExtractHandler.handleExtract(job, extract, this, ExtractSource.OUTPUT, output, getOutput());
        } else if (activityModel == null) {
            // deprecated SDK-140
            ExtractHandler.handleExtract(job, this, ExtractSource.OUTPUT, output, getOutput());
        }
        outputProcessed = true;
    }

    /**
     * handle tracing
     *
     * @param data
     * @param input
     */
    private void handleTracing(Object data, boolean input) {
        //first check if there is any data to handle
        if (data != null) {
            //if tracepoint has already been evaluted, trace is true, if not, check deeptrace
            if (!trace) {
                trace = job.isDeepTrace();
            }
            if (!trace) {
                //if no deeptrace, check if tracepoint requires tracing
                checkTracepoint();
            }
            if (trace) {
                //add trace data
                if (input) {
                    setInput(job.getNjams().serialize(data));
                } else {
                    setOutput(job.getNjams().serialize(data));
                }
                job.setTraces(true);
            }
        }
    }

    private void checkTracepoint() {
        ActivityConfiguration activityConfig = job.getActivityConfiguration(activityModel);
        if (activityConfig == null) {
            return;
        }
        TracepointExt tracepoint = activityConfig.getTracepoint();
        if (job.isActiveTracepoint(tracepoint)) {
            trace = true;
            tracepoint.increaseCurrentIterations();
            //activate deeptrace if needed
            if (tracepoint.isDeeptrace()) {
                job.setDeepTrace(true);
            }
        }
    }

    /**
     * Step to a new SubProcess with a given toSubProcessModel.
     *
     * @param toSubProcessModel to step to
     * @return the SubProcessBuilder for the new Group
     */
    @Override
    public SubProcessActivityBuilder stepToSubProcess(SubProcessActivityModel toSubProcessModel) {
        end();
        TransitionModel transitionModel = toSubProcessModel.getIncomingTransitionFrom(getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final SubProcessActivityImpl toSubProcess =
                (SubProcessActivityImpl) job.getActivityByModelId(toSubProcessModel.getId());
        final SubProcessActivityBuilder builder;
        if (toSubProcess == null || !Objects.equals(toSubProcess.getIteration(), getIteration())
                || toSubProcess.getParent() != getParent()) {
            builder = new SubProcessActivityBuilder(job, toSubProcessModel);
        } else {
            builder = new SubProcessActivityBuilder(toSubProcess);
        }
        builder.stepFrom(this, transitionModel);
        if (toSubProcessModel.getSubProcess() != null) {
            builder.setSubProcess(toSubProcessModel.getSubProcess());
        }
        return builder;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{model = " + getModelId() + ", instance = " + getInstanceId() + '}';
    }

    /**
     * Sets the EventStatus for this job. An EventStatus can be 0 for INFO 1 for
     * SUCCESS 2 for WARNING 3 for ERROR or null for no eventStatus. Anything
     * else will change nothing. The status of the corresponding job to this
     * activity will be set to SUCCESS, WARNING or ERROR likewise. For INFO only
     * the eventStatus will be set, but the job status will stay the same.
     *
     * @param eventStatus eventStatus to set.
     */
    @Override
    public void setEventStatus(Integer eventStatus) {
        try {
            //This will cause a NjamsSdkRuntimeException, if the eventStatus
            //is not valid.
            EventStatus.byValue(eventStatus);
            setExecutionIfNotSet();
            super.setEventStatus(eventStatus);
            if (eventStatus != null && 1 <= eventStatus && eventStatus <= 3) {
                JobStatus possibleStatus = JobStatus.byValue(eventStatus);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("This status was set to job {} : {}", job.getLogId(), possibleStatus);
                }
                job.setStatus(possibleStatus);
            }
        } catch (NjamsSdkRuntimeException e) {
            LOG.error("{} for job with logId: {}. Using old status: {}", e.getMessage(), job.getLogId(),
                    super.getEventStatus());
        }
    }

    private void setExecutionIfNotSet() {
        if (getExecution() == null) {
            setExecution(DateTimeUtility.now());
        }
    }

    /**
     * @return the estimatedSize
     */
    @XmlTransient
    public long getEstimatedSize() {
        return estimatedSize;
    }

    /**
     * Add to estimated size
     *
     * @param estimatedSize estimated size to add
     */
    public void addToEstimatedSize(long estimatedSize) {
        this.estimatedSize += estimatedSize;
    }

    /**
     * Process the startData. Checks if recording is activated for this job, and
     * decide if startdata will be needed.
     *
     * @param startData startData to process
     */
    @Override
    public void processStartData(Object startData) {
        if (job.isRecording()) {
            setStartData(job.getNjams().serialize(startData));
        }
    }

    /**
     * This method masks the input and calls its super method.
     *
     * @param input the input to mask and set to the Activity.
     */
    @Override
    public void setInput(String input) {
        super.setInput(DataMasking.maskString(input));
    }

    /**
     * This method masks the output and calls its super method.
     *
     * @param output the output to mask and set to the Activity.
     */
    @Override
    public void setOutput(String output) {
        super.setOutput(DataMasking.maskString(output));
    }

    /**
     * This method masks the eventMessage and calls its super method.
     *
     * @param message the eventMessage to mask and set to the Activity.
     */
    @Override
    public void setEventMessage(String message) {
        setExecutionIfNotSet();
        super.setEventMessage(message);
    }

    /**
     * This method masks the eventCode and calls its super method.
     *
     * @param code the eventCode to mask and set to the Activity.
     */
    @Override
    public void setEventCode(String code) {
        setExecutionIfNotSet();
        super.setEventCode(code);
    }

    /**
     * This method masks the eventPayload and calls its super method. After
     * that, it adds the size of the masked payload to the estimatedSize of this
     * and of the job.
     *
     * @param eventPayload the eventPayload to mask and set to the Activity.
     */
    @Override
    public void setEventPayload(String eventPayload) {
        setExecutionIfNotSet();
        super.setEventPayload(eventPayload);
        if (eventPayload != null) {
            int payloadSize = eventPayload.length();
            addToEstimatedSize(payloadSize);
            job.addToEstimatedSize(payloadSize);
        }
    }

    /**
     * This method masks the stackTrace and calls its super method. After that,
     * it adds the size of the masked stackTrace to the estimatedSize of this
     * and of the job.
     *
     * @param stackTrace the stackTrace to mask and set to the Activity.
     */
    @Override
    public void setStackTrace(String stackTrace) {
        setExecutionIfNotSet();
        super.setStackTrace(stackTrace);
        if (stackTrace != null) {
            int stackTraceSize = stackTrace.length();
            addToEstimatedSize(stackTraceSize);
            job.addToEstimatedSize(stackTraceSize);
        }
    }

    /**
     * This method masks the startData and calls its super method. After that,
     * it adds the size of the masked startData to the estimatedSize of this and
     * of the job.
     *
     * @param startData the startData to mask and set to the Activity.
     */
    @Override
    public void setStartData(String startData) {
        String maskedStartData = DataMasking.maskString(startData);
        super.setStartData(maskedStartData);
        if (maskedStartData != null) {
            int startDataSize = maskedStartData.length();
            addToEstimatedSize(startDataSize);
            job.addToEstimatedSize(startDataSize);
        }
    }

    /**
     * This method masks the attributes map and calls its super method.
     * Furthermore it adds the masked attributes to the jobs attributes aswell.
     *
     * @param attributes the attributes to mask and set to the Activity.
     */
    @Override
    public void setAttributes(Map<String, String> attributes) {
        synchronized (attributesLock) {
            attributes.forEach((key, value) -> job.addAttribute(key, value));
            super.setAttributes(attributes);
        }
    }

    /**
     * This method masks the attributes value and calls its super method.
     * Furthermore it adds the masked attribute to the jobs attributes aswell.
     *
     * @param key the key for the activities attribute map
     * @param value the value to mask and set to the Activity.
     */
    @Override
    public void addAttribute(String key, String value) {
        synchronized (attributesLock) {
            job.addAttribute(key, value);
            super.addAttribute(key, value);
        }
    }

    /**
     * This method returns the attributes of this activity as an unmodifiable
     * map. Use setAttributes or addAttribute to modify the Map.
     *
     * @return Unmodifiable map of attributes of this activity.
     */
    @Override
    public Map<String, String> getAttributes() {
        synchronized (attributesLock) {
            return Collections.unmodifiableMap(super.getAttributes());
        }
    }

    @XmlTransient
    ActivityModel getActivityModel() {
        return activityModel;
    }

}
