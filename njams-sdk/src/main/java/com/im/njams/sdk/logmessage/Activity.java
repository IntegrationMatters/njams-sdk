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

import com.faizsiegeln.njams.messageformat.v4.logmessage.interfaces.IActivity;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * The runtime Activity
 *
 * @author pnientiedt
 * @version 4.0.6
 */
public interface Activity extends IActivity {

    /**
     * Step to a new Activity with a given toActivityModelId.
     * @deprecated SDK-140 Does not work for sub-processes.
     * @param toActivityModelId to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Deprecated
    public ActivityBuilder stepTo(String toActivityModelId);

    /**
     * Step to a new Activity with a given toActivityModel.
     *
     * @param toActivityModel to step to
     * @return the ActivityBuilder for the new Activity
     */
    public ActivityBuilder stepTo(ActivityModel toActivityModel);

    /**
     * Step to a new Group with a given toGroupModelId.
     * @deprecated SDK-140 Does not work for sub-processes.
     * @param toGroupModelId to step to
     * @return the GroupBuilder for the new Group
     */
    @Deprecated
    public GroupBuilder stepToGroup(String toGroupModelId);

    /**
     * Step to a new Group with a given toGroupModel.
     *
     * @param toGroupModel to step to
     * @return the GroupBuilder for the new Group
     */
    public GroupBuilder stepToGroup(GroupModel toGroupModel);

    /**
     * Step to a new SubProcess with a given toSubProcessModel.
     *
     * @param toSubProcessModel to step to
     * @return the SubProcessBuilder for the new Group
     */
    public SubProcessActivityBuilder stepToSubProcess(SubProcessActivityModel toSubProcessModel);

    /**
     * return the parent of the current activity
     *
     * @return the parent
     */
    public Group getParent();

    /**
     * Adds a link to a predecessor activity which represents a transition in
     * the model
     *
     * @param predecessorInstanceId Instance ID of the direct predecessor
     * activity.
     * @param transitionModelId Model ID of the transition used to get from the
     * predecessor to this activity.
     */
    public void addPredecessor(String predecessorInstanceId, String transitionModelId);

    /**
     * Return if this ActivityImpl is a starter activity
     *
     * @return true if it is a starter activity
     */
    boolean isStarter();

    /**
     * Returns the event associated with this activity. If the activity does not
     * have an event, one will be created.
     *
     * @return The event of the activity
     */
    public Event createEvent();

    /**
     * End this activity
     */
    public void end();

    /**
     * Process the input for this activity. Checks for tracepoints, extracts and
     * similar functionality to decide if and how this input data will be
     * handled.
     *
     * @param input input data
     */
    public void processInput(Object input);

    /**
     * Process the output for this activity. Checks for tracepoints, extracts
     * and similar functionality to decide if and how this output data will be
     * handled.
     *
     * @param output output data
     */
    public void processOutput(Object output);

    /**
     * Process the startData. Checks if recording is activites for this job, and
     * decide if startdata will be needed
     *
     * @param startData startData to add
     */
    public void processStartData(Object startData);

    /**
     * Sets the EventStatus for this job. The status of the corresponding job to this
     * activity will be set to SUCCESS, WARNING or ERROR likewise. For INFO only
     * the eventStatus will be set, but the job status will stay the same.
     *
     * @param status eventStatus to set.
     */
    public void setEventStatus(EventStatus status);

    /**
     * Records that an error occurred when executing this activity instance . Whether or not an
     * according event is generated depends on the {@value JobImpl#LOG_ALL_ERRORS} setting,
     * or the job's end status reported by the executing engine.
     *
     * @param errorEvent Information about the error that occurred. This information is used for
     * generating an according event if required.
     */
    public void setActivityError(ErrorEvent errorEvent);
}
