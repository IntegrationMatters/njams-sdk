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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.annotation.XmlTransient;

import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.GroupModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Activity which represents a group in a process. A group can have child
 * activities.
 *
 * @author pnientiedt
 */
public class GroupImpl extends ActivityImpl implements Group {

    private final Map<String, ActivityImpl> childActivities = new LinkedHashMap<>();

    public GroupImpl(JobImpl job, ActivityModel model) {
        super(job, model);
        setMaxIterations(1L);
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

        /*
         * TODO: This kind of lookup seems odd as it looks for an activity with the same iteration as this group
         * which means that the activity in question is not inside the group but at the same level/scope as the
         * group itself. Further it shall have the same parent as the group which again means that it is not inside
         * the group.
         * It is not clear, for what case this implementation is meant for.
         * The same kind of lookup occurs in createChildGroup and createChildSubProcess, and in step-methods in
         * ActivityImpl etc.
         *
         * Finally it is not clear that this method may return an existing activity instance, wrapped into a builder.
         * The user cannot be aware that he's potentially manipulating an existing activity instance.
         *
         */
        if (toActivity == null || !Objects.equals(toActivity.getIteration(), getIteration())
                || toActivity.getParent() != getParent()) {
            builder = new ActivityBuilder((JobImpl) getJob(), childActivityModel);
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
        synchronized (childActivities) {
            childActivities.put(child.getInstanceId(), child);
        }
    }

    /**
     * return all child activities
     *
     * @return all child activities
     */
    @XmlTransient
    @Override
    public List<Activity> getChildActivities() {
        // TODO: Copying the values could be avoided by returning an unmodifiable view as Collection instead.
        // However, that could result in concurrency issues.
        // Hence, safely creating a copy seems to be good solution, but the copy does not need to be unmodifiable.
        synchronized (childActivities) {
            return new ArrayList<>(childActivities.values());
        }
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
            builder = new GroupBuilder((JobImpl) getJob(), childGroupModel);
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
        final SubProcessActivityImpl toSubProcess =
                (SubProcessActivityImpl) getJob().getActivityByModelId(childSubProcessModel.getId());
        final SubProcessActivityBuilder builder;
        if (toSubProcess == null || !Objects.equals(toSubProcess.getIteration(), getIteration())
                || toSubProcess.getParent() != getParent()) {
            builder = new SubProcessActivityBuilder((JobImpl) getJob(), childSubProcessModel);
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
     * This method removes the childActivity for the given InstanceId.
     *
     * @param instanceId the Id of the Activity to remove
     */
    public void removeChildActivity(String instanceId) {
        synchronized (childActivities) {
            childActivities.remove(instanceId);
        }
    }

    public ActivityBuilder newChildActivity(ActivityModel childActivityModel) {
        if (childActivityModel instanceof GroupModel) {
            return newChildGroup((GroupModel) childActivityModel);
        }
        if (childActivityModel instanceof SubProcessActivityModel) {
            return newChildSubProcess((SubProcessActivityModel) childActivityModel);
        }
        final ActivityBuilder builder;
        builder = new ActivityBuilder((JobImpl) getJob(), childActivityModel);
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    private GroupBuilder newChildGroup(GroupModel childGroupModel) {
        // check if a activity with the same modelId and the same iteration already exists.
        final GroupBuilder builder = new GroupBuilder((JobImpl) getJob(), childGroupModel);
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        return builder;
    }

    private SubProcessActivityBuilder newChildSubProcess(SubProcessActivityModel childSubProcessModel) {
        // check if a activity with the same modelId and the same iteration already exists.
        final SubProcessActivityBuilder builder =
                new SubProcessActivityBuilder((JobImpl) getJob(), childSubProcessModel);
        builder.setParent(this);
        builder.setIteration(getMaxIterations());
        if (childSubProcessModel.getSubProcess() != null) {
            builder.setSubProcess(childSubProcessModel.getSubProcess());
        }
        return builder;
    }
}
