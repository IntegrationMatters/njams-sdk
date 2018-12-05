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

import java.util.List;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Activity which represents a group in a process. A group can have child
 * activities.
 *
 * @author pnientiedt
 */
public interface Group extends Activity {

    /**
     * Creates a new Activity as child activity of the current activity.
     *
     * @param childActivityModelId to step to
     * @return the ActivityBuilder for the new Activity
     */
    public ActivityBuilder createChildActivity(String childActivityModelId);

    /**
     * Creates a new Activity as child activity of the current activity.
     *
     * @param childActivityModel to step to
     * @return the ActivityBuilder for the new Activity
     */
    public ActivityBuilder createChildActivity(ActivityModel childActivityModel);

    /**
     * Creates a new Group as child group of the current group.
     *
     * @param childGroupModelId to step to
     * @return the GroupBuilder for the new Group
     */
    public GroupBuilder createChildGroup(String childGroupModelId);

    /**
     * Creates a new Group as child group of the current group.
     *
     * @param childGroupModel to step to
     * @return the GroupBuilder for the new Group
     */
    public GroupBuilder createChildGroup(GroupModel childGroupModel);

    /**
     * Creates a new SubProcess as child activity of the current activity. This
     * makes the current activity to a group, if it is not already one.
     *
     * @param childSubProcessModel to step to
     * @return the SubProcessBuilder for the new Activity
     */
    public ActivityBuilder createChildSubProcess(SubProcessActivityModel childSubProcessModel);

    /**
     * Increase the iteration counter of the group. All activities added
     * afterwards will be added to a new iteration
     */
    public void iterate();

    /**
     * return all child activities in an unmodifiable list
     *
     * @return all child activities
     */
    public List<Activity> getChildActivities();
}
