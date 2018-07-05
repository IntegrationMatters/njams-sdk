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
package com.im.njams.sdk.logmessage;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.model.TransitionModel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import javax.xml.bind.annotation.XmlTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is internal implementation of the Activity. It is a extension of the
 * MesasgeFormat activity, and provided functionality for easy chaining
 * activities.
 *
 * @author pnientiedt
 */
public class ActivityImpl extends com.faizsiegeln.njams.messageformat.v4.logmessage.Activity implements Activity {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityImpl.class);

    // Job will be hold because it will be needed when creating the next activity via stepTo logic
    private final JobImpl job;
    private boolean starter;
    private GroupImpl parent = null;
    private boolean trace;

    private long estimatedSize = 700L;
    private boolean ended = false;
    private boolean inputProcessecd = false;
    private boolean outputProcessed = false;

    /**
     * Create new ActivityImpl for a given Job
     *
     * @param job job which the new ActivitiyImpl belongs to
     */
    public ActivityImpl(JobImpl job) {
        this.job = job;
        job.addToEstimatedSize(estimatedSize);
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
     *
     * @param toActivityModelId to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
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
        TransitionModel transitionModel = toActivityModel.getIncomingTransitionFrom(this.getModelId());
        if (transitionModel == null) {
            throw new NjamsSdkRuntimeException("No transition from "
                    + getModelId() + " to " + toActivityModel.getId() + " fround!");
        }
        end();
        //check if a activity with the same modelId and the same iteration and parent already exists.
        final ActivityImpl toActivity = (ActivityImpl) job.getActivityByModelId(toActivityModel.getId());
        final ActivityBuilder builder;
        if (toActivity == null
                || !Objects.equals(toActivity.getIteration(), getIteration())
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
     *
     * @param toGroupModelId to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
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
        TransitionModel transitionModel = toGroupModel.getIncomingTransitionFrom(this.getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final GroupImpl toGroup = (GroupImpl) job.getActivityByModelId(toGroupModel.getId());
        final GroupBuilder builder;
        if (toGroup == null || !Objects.equals(toGroup.getIteration(), getIteration()) || toGroup.getParent() != getParent()) {
            builder = new GroupBuilder(job, toGroupModel);
        } else {
            builder = new GroupBuilder(toGroup);
        }
        builder.stepFrom(this, transitionModel);
        return builder;
    }

    /**
     * Returns the event associated with this activity. If the activity does not
     * jave an event, one will be created.
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
        setExecution(DateTimeUtility.now());
        if (getActivityStatus() == null) {
            setActivityStatus(ActivityStatus.RUNNING);
        }
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
        ExtractHandler.handleExtract(job, this, "in", input, getInput());
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
        setDuration(Duration.between(getExecution(), DateTimeUtility.now()).toMillis());
        if (getActivityStatus() == ActivityStatus.RUNNING) {
            setActivityStatus(ActivityStatus.SUCCESS);
        }
        if (this instanceof GroupImpl) {
            ((GroupImpl) this).getChildActivities().stream()
                    .filter(a -> a.getActivityStatus() == ActivityStatus.RUNNING)
                    .forEach(a -> a.end());
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
        ExtractHandler.handleExtract(job, this, "out", output, getOutput());
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
                    ((com.faizsiegeln.njams.messageformat.v4.logmessage.Activity) this)
                            .setInput(DataMasking.maskString(job.getProcessModel().getNjams().serialize(data)));
                } else {
                    ((com.faizsiegeln.njams.messageformat.v4.logmessage.Activity) this)
                            .setOutput(DataMasking.maskString(job.getProcessModel().getNjams().serialize(data)));
                }
                job.setTraces(trace);
            }
        }
    }

    private void checkTracepoint() {
        //check tracepoint
        TracepointExt tracepoint = job.getTracepoint(getModelId());
        if (tracepoint != null) {
            //if tracepoint exists, check timings
            LocalDateTime now = DateTimeUtility.now();
            if (now.isAfter(tracepoint.getStarttime())
                    && now.isBefore(tracepoint.getEndtime())
                    && !tracepoint.iterationsExceeded()) {
                //timing is right, and iterations are less than configured
                trace = true;
                tracepoint.increaseCurrentIterations();
                //activate deeptrace if needed
                if (tracepoint.isDeeptrace()) {
                    job.setDeepTrace(true);
                }
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
        TransitionModel transitionModel = toSubProcessModel.getIncomingTransitionFrom(this.getModelId());
        //check if a activity with the same modelId and the same iteration already exists.
        final SubProcessActivityImpl toSubProcess = (SubProcessActivityImpl) job.getActivityByModelId(toSubProcessModel.getId());
        final SubProcessActivityBuilder builder;
        if (toSubProcess == null || !Objects.equals(toSubProcess.getIteration(), getIteration()) || toSubProcess.getParent() != getParent()) {
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
        return getClass().getSimpleName() + "{model = " + this.getModelId() + ", instance = " + this.getInstanceId() + '}';
    }

    /**
     * Set the eventStatus
     *
     * @param eventStatus eventStatus to set
     */
    @Override
    public void setEventStatus(Integer eventStatus) {
        super.setEventStatus(eventStatus); //To change body of generated methods, choose Tools | Templates.
        job.setStatus(JobStatus.byValue(eventStatus));
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
     * Set the eventPayload
     *
     * @param eventPayload eventPayload to set
     */
    @Override
    public void setEventPayload(String eventPayload) {
        super.setEventPayload(eventPayload);
        if (eventPayload != null) {
            addToEstimatedSize(eventPayload.length());
            job.addToEstimatedSize(eventPayload.length());
        }
    }

    /**
     * Set the stackTrace
     *
     * @param stackTrace stackTrace to set
     */
    @Override
    public void setStackTrace(String stackTrace) {
        super.setStackTrace(stackTrace);
        if (stackTrace != null) {
            addToEstimatedSize(stackTrace.length());
            job.addToEstimatedSize(stackTrace.length());
        }
    }

    /**
     * Set the startData
     *
     * @param startData startData to set
     */
    @Override
    public void setStartData(String startData) {
        super.setStartData(startData);
        if (startData != null) {
            addToEstimatedSize(startData.length());
            job.addToEstimatedSize(startData.length());
        }
    }

    /**
     * Process the startData. Checks if recording is activites for this job, and
     * decide if startdata will be needed
     *
     * @param startData startData to process
     */
    @Override
    public void processStartData(Object startData) {
        if (job.isRecording()) {
            setStartData(job.getProcessModel().getNjams().serialize(startData));
        }
    }
}
