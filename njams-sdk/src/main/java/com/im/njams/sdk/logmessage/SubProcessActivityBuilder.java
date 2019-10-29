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
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import com.im.njams.sdk.model.TransitionModel;

/**
 * ActivityBuilder used to build a SubProcessActivity
 *
 * @author pnientiedt
 */
public class SubProcessActivityBuilder extends GroupBuilder {

    SubProcessActivityBuilder(JobImpl job, SubProcessActivityModel model) {
        super(new SubProcessActivityImpl(job, model));
        getActivity().setSequence(job.getNextSequence());
    }

    SubProcessActivityBuilder(SubProcessActivityImpl subProcess) {
        super(subProcess);
    }

    /**
     * Build the activity, by creating a valid instanceId and adding it to the
     * job.
     *
     * @return the created Activity
     */
    @Override
    public SubProcessActivity build() {
        return (SubProcessActivity) super.build();
    }

    /**
     * Set the itration for the SubProcessActivity
     *
     * @param iteration iteration to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setIteration(Long iteration) {
        return (SubProcessActivityBuilder) super.setIteration(iteration);
    }

    /**
     * Set maxIteration for the SubProcessActivity
     *
     * @param maxIteration maxIteration to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setMaxIterations(Long maxIteration) {
        return (SubProcessActivityBuilder) super.setMaxIterations(maxIteration);
    }

    /**
     * Set parentInstanceId for the SubProcessActivity
     *
     * @param parentInstanceId parentInstanceId to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setParentInstanceId(String parentInstanceId) {
        return (SubProcessActivityBuilder) super.setParentInstanceId(parentInstanceId);
    }

    /**
     * Set execution for the SubProcessActivity
     *
     * @param execution execution to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setExecution(LocalDateTime execution) {
        return (SubProcessActivityBuilder) super.setExecution(execution);
    }

    /**
     * Set duration for the SubProcessActivity
     *
     * @param duration duration to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setDuration(long duration) {
        return (SubProcessActivityBuilder) super.setDuration(duration);
    }

    /**
     * Set cpuTime for the SubProcessActivity
     *
     * @param cpuTime cpuTime to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setCpuTime(long cpuTime) {
        return (SubProcessActivityBuilder) super.setCpuTime(cpuTime);
    }

    /**
     * Set activityStatus for the SubProcessActivity
     *
     * @param activityStatus activityStatus to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setActivityStatus(ActivityStatus activityStatus) {
        return (SubProcessActivityBuilder) super.setActivityStatus(activityStatus);
    }

    /**
     * Set eventStatus for the SubProcessActivity
     *
     * @param eventStatus eventStatus to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setEventStatus(Integer eventStatus) {
        return (SubProcessActivityBuilder) super.setEventStatus(eventStatus);
    }

    /**
     * Set eventMessage for the SubProcessActivity
     *
     * @param eventMessage eventMessage to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setEventMessage(String eventMessage) {
        return (SubProcessActivityBuilder) super.setEventMessage(eventMessage);
    }

    /**
     * Set eventCode for the SubProcessActivity
     *
     * @param eventCode eventCode to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setEventCode(String eventCode) {
        return (SubProcessActivityBuilder) super.setEventCode(eventCode);
    }

    /**
     * Set eventPayload for the SubProcessActivity
     *
     * @param eventPayload eventPayload to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setEventPayload(String eventPayload) {
        return (SubProcessActivityBuilder) super.setEventPayload(eventPayload);
    }

    /**
     * Set stackTrace for the SubProcessActivity
     *
     * @param stackTrace stackTrace to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setStackTrace(String stackTrace) {
        return (SubProcessActivityBuilder) super.setStackTrace(stackTrace);
    }

    /**
     * Set startData for the SubProcessActivity
     *
     * @param startData startData to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setStartData(String startData) {
        return (SubProcessActivityBuilder) super.setStartData(startData);
    }

    /**
     * Set attribute key and value for the SubProcessActivity
     *
     * @param key attribute key to set
     * @param value attribute value to set
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder addAttribute(String key, String value) {
        return (SubProcessActivityBuilder) super.addAttribute(key, value);
    }

    /**
     * Mark the SubProcessActivity as a starter
     *
     * @return this builder
     */
    @Override
    public SubProcessActivityBuilder setStarter() {
        return (SubProcessActivityBuilder) super.setStarter();
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity to the
     * currently build activity. A transitionId will be created automatically
     *
     * @param fromActivity Predecessor activity
     * @return this builder
     */
    @Override
    SubProcessActivityBuilder stepFrom(Activity fromActivity) {
        return (SubProcessActivityBuilder) super.stepFrom(fromActivity);
    }

    /**
     * Adds a Predecessor pointing to the given previous Activity and
     * TransitionModel to the currently build activity.
     *
     * @param fromActivity Predecessor activity
     * @return this builder
     */
    @Override
    SubProcessActivityBuilder stepFrom(Activity fromActivity, TransitionModel model) {
        return (SubProcessActivityBuilder) super.stepFrom(fromActivity, model);
    }

    /**
     * Set the parent
     *
     * @param parent Parent
     * @return this builder
     */
    @Override
    SubProcessActivityBuilder setParent(GroupImpl parent) {
        return (SubProcessActivityBuilder) super.setParent(parent);
    }

    /**
     * Set subProcess for the SubProcessActivity
     *
     * @param subProcess ProcessModel of the subProcess
     * @return this builder
     */
    public SubProcessActivityBuilder setSubProcess(ProcessModel subProcess) {
        ((SubProcessActivityImpl) super.getActivity()).setSubProcess(subProcess);
        return this;
    }
}
