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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;
import java.util.Iterator;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Activity which represents a group in a process. A group can have child
 * activities.
 *
 * @author pnientiedt
 */
public class GroupImpl extends ActivityImpl implements Group {

    private final Map<String, ActivityImpl> childActivities = new LinkedHashMap<>();

    /**
     * Create a new GroupImpl
     *
     * @param job Job which should contain this GroupImpl
     */
    public GroupImpl(JobImpl job) {
        super(job);
        setMaxIterations(1L);
    }

    /**
     * Creates a new Activity as child activity of the current activity. This
     * makes the current activity to a group, if it is not already one.
     *
     * @param childActivityModelId to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
    public ActivityBuilder createChildActivity(String childActivityModelId) {
        ActivityBuilder builder = new ActivityBuilder((JobImpl) getJob(), childActivityModelId);
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    /**
     * Creates a new Activity as child activity of the current activity. This
     * makes the current activity to a group, if it is not already one.
     *
     * @param childActivityModel to step to
     * @return the ActivityBuilder for the new Activity
     */
    @Override
    public ActivityBuilder createChildActivity(ActivityModel childActivityModel) {
        if (childActivityModel instanceof GroupModel) {
            return createChildGroup((GroupModel) childActivityModel);
        }
        if (childActivityModel instanceof SubProcessActivityModel) {
            return createChildSubProcess((SubProcessActivityModel) childActivityModel);
        }
        // check if a activity with the same modelId and the same iteration already exists.
        final ActivityImpl toActivity = (ActivityImpl) getJob().getActivityByModelId(childActivityModel.getId());
        final ActivityBuilder builder;
        if (toActivity == null || !Objects.equals(toActivity.getIteration(), getIteration())
                || toActivity.getParent() != getParent()) {
            builder = new ActivityBuilder((JobImpl) getJob(), childActivityModel.getId());
        } else {
            builder = new ActivityBuilder(toActivity);
        }
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    /**
     * Add a new child activity
     *
     * @param child ActivityImpl of the child
     */
    public void addChild(ActivityImpl child) {
        childActivities.put(child.getInstanceId(), child);
    }

    /**
     * return all child activities in an unmodifiable list
     *
     * @return all child activities
     */
    @XmlTransient
    @Override
    public List<Activity> getChildActivities() {
        return Collections.unmodifiableList(new ArrayList<>(childActivities.values()));
    }

    /**
     * Increase the iteration counter of the group. All activities added
     * afterwards will be added to a new iteration
     */
    @Override
    public void iterate() {
        setMaxIterations(getMaxIterations() + 1);
    }

    /**
     * Creates a new Group as child group of the current group.
     *
     * @param childGroupModelId to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
    public GroupBuilder createChildGroup(String childGroupModelId) {
        GroupBuilder builder = new GroupBuilder((JobImpl) getJob(), childGroupModelId);
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    /**
     * Creates a new Group as child group of the current group.
     *
     * @param childGroupModel to step to
     * @return the GroupBuilder for the new Group
     */
    @Override
    public GroupBuilder createChildGroup(GroupModel childGroupModel) {
        // check if a activity with the same modelId and the same iteration already exists.
        final GroupImpl toGroup = (GroupImpl) getJob().getActivityByModelId(childGroupModel.getId());
        final GroupBuilder builder;
        if (toGroup == null || !Objects.equals(toGroup.getIteration(), getIteration())
                || toGroup.getParent() != getParent()) {
            builder = new GroupBuilder((JobImpl) getJob(), childGroupModel.getId());
        } else {
            builder = new GroupBuilder(toGroup);
        }
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    /**
     * Creates a new SubProcess as child activity of the current activity. This
     * makes the current activity to a group, if it is not already one.
     *
     * @param childSubProcessModel to step to
     * @return the SubProcessBuilder for the new Activity
     */
    @Override
    public SubProcessActivityBuilder createChildSubProcess(SubProcessActivityModel childSubProcessModel) {
        // check if a activity with the same modelId and the same iteration already exists.
        final SubProcessActivityImpl toSubProcess = (SubProcessActivityImpl) getJob().getActivityByModelId(childSubProcessModel.getId());
        final SubProcessActivityBuilder builder;
        if (toSubProcess == null || !Objects.equals(toSubProcess.getIteration(), getIteration())
                || toSubProcess.getParent() != getParent()) {
            builder = new SubProcessActivityBuilder((JobImpl) getJob(), childSubProcessModel.getId());
        } else {
            builder = new SubProcessActivityBuilder(toSubProcess);
        }
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        if (childSubProcessModel.getSubProcess() != null) {
            builder.setSubProcess(childSubProcessModel.getSubProcess());
        }
        return builder;
    }

    /**
     * This method removes all the childActivities that aren't running, therefore
     * the map will be kept small.
     */
    @Override
    public void removeNotRunningChildActivities() {
        Iterator<String> iterator = childActivities.keySet().iterator();
        while (iterator.hasNext()) {
            Activity a = childActivities.get(iterator.next());
            if (a.getActivityStatus() != ActivityStatus.RUNNING) {
                iterator.remove();
            }
        }
    }
}
