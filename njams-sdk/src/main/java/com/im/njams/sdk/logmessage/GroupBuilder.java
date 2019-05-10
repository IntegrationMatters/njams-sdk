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
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.TransitionModel;

/**
 * This Builder will be used to create groups. To get this Builder, use the
 * start function on the Job Object or the stepTo function on the previously
 * created Activity.
 *
 * @author pnientiedt
 */
public class GroupBuilder extends ActivityBuilder {

    @Deprecated
    GroupBuilder(JobImpl job, String modelId) {
        super(getModel(job, modelId) != null ? new GroupImpl(job, getModel(job, modelId))
                : new GroupImpl(job, modelId));
        getActivity().setSequence(job.getNextSequence());
    }

    GroupBuilder(JobImpl job, GroupModel model) {
        super(new GroupImpl(job, model));
        getActivity().setSequence(job.getNextSequence());
    }

    GroupBuilder(GroupImpl group) {
        super(group);
    }

    @Deprecated
    private static GroupModel getModel(JobImpl job, String modelId) {
        return (GroupModel) job.getProcessModel().getActivity(modelId);
    }

    /**
     * Build the activity, by creating a valid instanceId and adding it to the
     * job.
     *
     * @return the created Activity
     */
    @Override
    public Group build() {
        return (Group) super.build();
    }

    /**
     * Set the itration for the GroupBuilder
     *
     * @param iteration iteration to set
     * @return this builder
     */
    @Override
    public GroupBuilder setIteration(Long iteration) {
        return (GroupBuilder) super.setIteration(iteration);
    }

    /**
     * Set maxIteration for the GroupBuilder
     *
     * @param maxIteration maxIteration to set
     * @return this builder
     */
    @Override
    public GroupBuilder setMaxIterations(Long maxIteration) {
        return (GroupBuilder) super.setMaxIterations(maxIteration);
    }

    /**
     * Set parentInstanceId for the GroupBuilder
     *
     * @param parentInstanceId parentInstanceId to set
     * @return this builder
     */
    @Override
    public GroupBuilder setParentInstanceId(String parentInstanceId) {
        return (GroupBuilder) super.setParentInstanceId(parentInstanceId);
    }

    /**
     * Set execution for the GroupBuilder
     *
     * @param execution execution to set
     * @return this builder
     */
    @Override
    public GroupBuilder setExecution(LocalDateTime execution) {
        return (GroupBuilder) super.setExecution(execution);
    }

    /**
     * Set duration for the GroupBuilder
     *
     * @param duration duration to set
     * @return this builder
     */
    @Override
    public GroupBuilder setDuration(long duration) {
        return (GroupBuilder) super.setDuration(duration);
    }

    /**
     * Set cpuTime for the GroupBuilder
     *
     * @param cpuTime cpuTime to set
     * @return this builder
     */
    @Override
    public GroupBuilder setCpuTime(long cpuTime) {
        return (GroupBuilder) super.setCpuTime(cpuTime);
    }

    /**
     * Set activityStatus for the GroupBuilder
     *
     * @param activityStatus activityStatus to set
     * @return this builder
     */
    @Override
    public GroupBuilder setActivityStatus(ActivityStatus activityStatus) {
        return (GroupBuilder) super.setActivityStatus(activityStatus);
    }

    /**
     * Set eventStatus for the GroupBuilder
     *
     * @param eventStatus eventStatus to set
     * @return this builder
     */
    @Override
    public GroupBuilder setEventStatus(Integer eventStatus) {
        return (GroupBuilder) super.setEventStatus(eventStatus);
    }

    /**
     * Set eventMessage for the GroupBuilder
     *
     * @param eventMessage eventMessage to set
     * @return this builder
     */
    @Override
    public GroupBuilder setEventMessage(String eventMessage) {
        return (GroupBuilder) super.setEventMessage(eventMessage);
    }

    /**
     * Set eventCode for the GroupBuilder
     *
     * @param eventCode eventCode to set
     * @return this builder
     */
    @Override
    public GroupBuilder setEventCode(String eventCode) {
        return (GroupBuilder) super.setEventCode(eventCode);
    }

    /**
     * Set eventPayload for the GroupBuilder
     *
     * @param eventPayload eventPayload to set
     * @return this builder
     */
    @Override
    public GroupBuilder setEventPayload(String eventPayload) {
        return (GroupBuilder) super.setEventPayload(eventPayload);
    }

    /**
     * Set stackTrace for the GroupBuilder
     *
     * @param stackTrace stackTrace to set
     * @return this builder
     */
    @Override
    public GroupBuilder setStackTrace(String stackTrace) {
        return (GroupBuilder) super.setStackTrace(stackTrace);
    }

    /**
     * Set startData for the GroupBuilder
     *
     * @param startData startData to set
     * @return this builder
     */
    @Override
    public GroupBuilder setStartData(String startData) {
        return (GroupBuilder) super.setStartData(startData);
    }

    /**
     * Set attribute key and value for the GroupBuilder
     *
     * @param key attribute key to set
     * @param value attribute value to set
     * @return this builder
     */
    @Override
    public GroupBuilder addAttribute(String key, String value) {
        return (GroupBuilder) super.addAttribute(key, value);
    }

    /**
     * Mark the GroupBuilder as a starter
     *
     * @return this builder
     */
    @Override
    public GroupBuilder setStarter() {
        return (GroupBuilder) super.setStarter();
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity to the
     * currently build activity. A transitionId will be created automatically
     *
     * @param fromActivity
     * @return
     */
    @Override
    GroupBuilder stepFrom(Activity fromActivity) {
        return (GroupBuilder) super.stepFrom(fromActivity);
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity and
     * TransitionModel to the currently build activity.
     *
     * @param fromActivity
     * @return
     */
    @Override
    GroupBuilder stepFrom(Activity fromActivity, TransitionModel model) {
        return (GroupBuilder) super.stepFrom(fromActivity, model);
    }

    /**
     * Set the parent
     *
     * @param parent Parent
     * @return this builder
     */
    @Override
    GroupBuilder setParent(GroupImpl parent) {
        return (GroupBuilder) super.setParent(parent);
    }

}
