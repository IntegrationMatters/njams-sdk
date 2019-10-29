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

import java.time.LocalDateTime;

import com.faizsiegeln.njams.messageformat.v4.logmessage.ActivityStatus;
import com.faizsiegeln.njams.messageformat.v4.logmessage.Predecessor;
import com.im.njams.sdk.common.IdUtil;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.TransitionModel;

/**
 * This Builder will be used to create activities. To get this Builder, use the
 * start function on the Job Object or the stepTo function on the previously
 * created Activity.
 *
 * @author stkniep
 */
public class ActivityBuilder {

    //The activity which will be build by this builder
    private final ActivityImpl activity;

    ActivityBuilder(JobImpl job, ActivityModel model) {
        activity = new ActivityImpl(job, model);
        activity.setSequence(job.getNextSequence());
    }

    ActivityBuilder(ActivityImpl activity) {
        this.activity = activity;
    }

    ActivityImpl getActivity() {
        return activity;
    }

    /**
     * Build the activity, by creating a valid instanceId and adding it to the
     * job.
     *
     * @return the created Activity
     */
    public Activity build() {
        buildInstanceId();
        //if this activity is a child of a group, add it to the parent
        if (activity.getParent() != null) {
            activity.getParent().addChild(activity);
        }
        activity.getJob().addActivity(activity);
        activity.start();
        return activity;
    }

    /**
     * Set the instanteId for the ActivityBuilder
     *
     * @param instanceId instanceId to set
     * @return this builder
     */
    public ActivityBuilder setInstanceId(String instanceId) {
        activity.setInstanceId(instanceId);
        return this;
    }

    /**
     * Build a new instanceId based on modelId and sequence
     */
    private void buildInstanceId() {
        if (activity.getModelId() == null || activity.getModelId().isEmpty()) {
            throw new NjamsSdkRuntimeException("ModelId not set");
        }
        if (activity.getSequence() == null) {
            throw new NjamsSdkRuntimeException("Sequence not set");
        }
        if (activity.getInstanceId() == null || activity.getInstanceId().isEmpty()) {
            String instanceId = IdUtil.getActivityInstanceId(activity.getModelId(), activity.getSequence());
            activity.setInstanceId(instanceId);
        }
    }

    /**
     * Set the itration for the ActivityBuilder
     *
     * @param iteration iteration to set
     * @return this builder
     */
    public ActivityBuilder setIteration(Long iteration) {
        activity.setIteration(iteration);
        return this;
    }

    /**
     * Set maxIteration for the ActivityBuilder
     *
     * @param maxIteration maxIteration to set
     * @return this builder
     */
    public ActivityBuilder setMaxIterations(Long maxIteration) {
        activity.setMaxIterations(maxIteration);
        return this;
    }

    /**
     * Set parentInstanceId for the ActivityBuilder
     *
     * @param parentInstanceId parentInstanceId to set
     * @return this builder
     */
    public ActivityBuilder setParentInstanceId(String parentInstanceId) {
        activity.setParentInstanceId(parentInstanceId);
        return this;
    }

    /**
     * Set execution for the ActivityBuilder
     *
     * @param execution execution to set
     * @return this builder
     */
    public ActivityBuilder setExecution(LocalDateTime execution) {
        activity.setExecution(execution);
        return this;
    }

    /**
     * Set duration for the ActivityBuilder
     *
     * @param duration duration to set
     * @return this builder
     */
    public ActivityBuilder setDuration(long duration) {
        activity.setDuration(duration);
        return this;
    }

    /**
     * Set cpuTime for the ActivityBuilder
     *
     * @param cpuTime cpuTime to set
     * @return this builder
     */
    public ActivityBuilder setCpuTime(long cpuTime) {
        activity.setCpuTime(cpuTime);
        return this;
    }

    /**
     * Set activityStatus for the ActivityBuilder
     *
     * @param activityStatus activityStatus to set
     * @return this builder
     */
    public ActivityBuilder setActivityStatus(ActivityStatus activityStatus) {
        activity.setActivityStatus(activityStatus);
        return this;
    }

    /**
     * Set eventStatus for the ActivityBuilder
     *
     * @param eventStatus eventStatus to set
     * @return this builder
     */
    public ActivityBuilder setEventStatus(Integer eventStatus) {
        activity.setEventStatus(eventStatus);
        return this;
    }

    /**
     * Set eventMessage for the ActivityBuilder
     *
     * @param eventMessage eventMessage to set
     * @return this builder
     */
    public ActivityBuilder setEventMessage(String eventMessage) {
        activity.setEventMessage(eventMessage);
        return this;
    }

    /**
     * Set eventCode for the ActivityBuilder
     *
     * @param eventCode eventCode to set
     * @return this builder
     */
    public ActivityBuilder setEventCode(String eventCode) {
        activity.setEventCode(eventCode);
        return this;
    }

    /**
     * Set eventPayload for the ActivityBuilder
     *
     * @param eventPayload eventPayload to set
     * @return this builder
     */
    public ActivityBuilder setEventPayload(String eventPayload) {
        activity.setEventPayload(eventPayload);
        return this;
    }

    /**
     * Set stackTrace for the ActivityBuilder
     *
     * @param stackTrace stackTrace to set
     * @return this builder
     */
    public ActivityBuilder setStackTrace(String stackTrace) {
        activity.setStackTrace(stackTrace);
        return this;
    }

    /**
     * Set startData for the ActivityBuilder
     *
     * @param startData startData to set
     * @return this builder
     */
    public ActivityBuilder setStartData(String startData) {
        activity.setStartData(startData);
        return this;
    }

    /**
     * Set attribute key and value for the ActivityBuilder
     *
     * @param key attribute key to set
     * @param value attribute value to set
     * @return this builder
     */
    public ActivityBuilder addAttribute(String key, String value) {
        activity.addAttribute(key, value);
        return this;
    }

    /**
     * Mark the ActivityBuilder as a starter
     *
     * @return this builder
     */
    public ActivityBuilder setStarter() {
        activity.setStarter(true);
        return this;
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity to the
     * currently build activity. A transitionId will be created automatically
     *
     * @param fromActivity
     * @return
     */
    ActivityBuilder stepFrom(Activity fromActivity) {
        if (fromActivity == null) {
            throw new NjamsSdkRuntimeException("stepFrom parameter can not be " + fromActivity);
        }
        buildInstanceId();
        String modelId = IdUtil.getTransitionModelId(fromActivity.getModelId(), activity.getModelId());
        activity.getPredecessors().add(new Predecessor(modelId, fromActivity.getInstanceId()));
        setGroupParameters(fromActivity);
        return this;
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity and
     * TransitionModel to the currently build activity.
     *
     * @param fromActivity
     * @return
     */
    ActivityBuilder stepFrom(Activity fromActivity, TransitionModel model) {
        if (fromActivity == null) {
            throw new NjamsSdkRuntimeException("stepFrom fromActivity parameter can not be " + fromActivity);
        }
        if (model == null) {
            throw new NjamsSdkRuntimeException("stepFrom model parameter can not be " + model);
        }
        activity.getPredecessors().add(new Predecessor(model.getId(), fromActivity.getInstanceId()));
        setGroupParameters(fromActivity);
        return this;
    }

    private void setGroupParameters(Activity fromActivity) {
        ActivityImpl from = (ActivityImpl) fromActivity;
        if (from.getParent() != null) {
            setParent(from.getParent());
        }
        if (fromActivity.getIteration() != null) {
            activity.setIteration(fromActivity.getIteration());
        }
    }

    /**
     * Set the parent
     *
     * @param parent Parent
     * @return this builder
     */
    ActivityBuilder setParent(GroupImpl parent) {
        activity.setParent(parent);
        setParentInstanceId(parent.getInstanceId());
        return this;
    }
}
