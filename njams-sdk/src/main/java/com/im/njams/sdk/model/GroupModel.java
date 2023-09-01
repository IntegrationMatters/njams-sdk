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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * GroupModel is a Activity which represents a Group ind the ProcessModel. A
 * Group can have child Activities.
 *
 * @author pnientiedt
 */
public class GroupModel extends ActivityModel {

    private final Map<String, ActivityModel> childActivities = new LinkedHashMap<>();
    private final Map<String, TransitionModel> childTransitions = new LinkedHashMap<>();
    private final Map<String, ActivityModel> startActivities = new LinkedHashMap<>();

    private int width;
    private int height;

    /**
     * Create new GroupModel
     *
     * @param processModel ProcessModel which should contain this GroupModel
     */
    public GroupModel(ProcessModel processModel) {
        super(processModel);
    }

    /**
     * Create new GroupModel
     *
     * @param processModel ProcessModel which should contain this GroupModel
     * @param id Id of this Activity
     * @param name Name of this Activity
     * @param type Type of this Activity
     */
    public GroupModel(ProcessModel processModel, String id, String name, String type) {
        super(processModel, id, name, type);
    }

    /**
     * Create a new {@link ActivityModel} in this {@link GroupModel}.
     *
     * @param activityModelId * of Actvity to create
     * @param activityName of Actvity to create
     * @param activityType of Actvity to create
     * @return the created {@link ActivityModel}
     */
    public ActivityModel createChildActivity(final String activityModelId, final String activityName,
        final String activityType) {
        if (this.getId().equals(activityModelId)) {
            throw new NjamsSdkRuntimeException("Child must differ from parent: " + this.getId());
        }
        final ActivityModel activity = getProcessModel().createActivity(activityModelId, activityName, activityType);
        addChildActivity(activity);
        return activity;
    }

    /**
     * Create a new {@link GroupModel} in this {@link GroupModel}.
     *
     * @param groupModelId of Actvity to create
     * @param groupName of Actvity to create
     * @param groupType of Actvity to create
     * @return the created {@link GroupModel}
     */
    public GroupModel createChildGroup(final String groupModelId, final String groupName, final String groupType) {
        if (this.getId().equals(groupModelId)) {
            throw new NjamsSdkRuntimeException("Child must differ from parent: " + this.getId());
        }
        final GroupModel group = getProcessModel().createGroup(groupModelId, groupName, groupType);
        addChildActivity(group);
        return group;
    }

    /**
     * Create a new {@link SubProcessActivityModel} in this {@link GroupModel}.
     *
     * @param subProcessModelId of Actvity to create
     * @param subProcessName of Actvity to create
     * @param subProcessType of Actvity to create
     * @return the created {@link GroupModel}
     */
    public SubProcessActivityModel createChildSubProcess(final String subProcessModelId, final String subProcessName,
        final String subProcessType) {
        if (this.getId().equals(subProcessModelId)) {
            throw new NjamsSdkRuntimeException("Child must differ from parent: " + this.getId());
        }
        final SubProcessActivityModel subProcess =
            getProcessModel().createSubProcess(subProcessModelId, subProcessName, subProcessType);
        addChildActivity(subProcess);
        return subProcess;
    }

    /**
     * Get childActivities
     *
     * @return a list with all children
     */
    public List<ActivityModel> getChildActivities() {
        return Collections.unmodifiableList(new ArrayList(childActivities.values()));
    }

    /**
     * Get childTransitions
     *
     * @return a list with all children
     */
    public List<TransitionModel> getChildTransitions() {
        return Collections.unmodifiableList(new ArrayList(childTransitions.values()));
    }

    /**
     * Adds a child to this Group, if it does not already exists.
     *
     * @param childActivity new child
     */
    public void addChildActivity(ActivityModel childActivity) {
        ActivityModel activity = childActivities.get(childActivity.getId());
        if (activity == null) {
            childActivities.put(childActivity.getId(), childActivity);
            childActivity.setParent(this);
        } else if (activity != childActivity) {
            throw new NjamsSdkRuntimeException(
                "A child activity with id " + childActivity.getId() + " already exists for " + getId());
        }
    }

    /**
     * Adds a child to this Group, if it does not already exists.
     *
     * @param childTransition new child
     */
    public void addChildTransition(TransitionModel childTransition) {
        TransitionModel transition = childTransitions.get(childTransition.getId());
        if (transition == null) {
            childTransitions.put(childTransition.getId(), childTransition);
            childTransition.setParent(this);
        } else if (transition != childTransition) {
            throw new NjamsSdkRuntimeException(
                "A child transition with id " + childTransition.getId() + " already exists for " + getId());
        }
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @param width the width to set
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @param height the height to set
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Return all starter activities
     *
     * @return all starter activities
     */
    public List<ActivityModel> getStartActivities() {
        return Collections.unmodifiableList(new ArrayList<>(startActivities.values()));
    }

    /**
     * Add starter activity
     *
     * @param activity Activity which is a starter
     */
    public void addStartActivity(ActivityModel activity) {
        if (!activity.isStarter()) {
            activity.setStarter(true);
        }
        startActivities.put(activity.getId(), activity);
    }

    /**
     * Remove start activity
     *
     * @param activity Starter activity which should be removed
     */
    public void removeStartActivity(ActivityModel activity) {
        if (activity.isStarter()) {
            activity.setStarter(false);
        }
        startActivities.remove(activity.getId());
    }

    /**
     * Return all child groups
     *
     * @return All child groups
     */
    public Collection<GroupModel> getChildGroups() {
        return childActivities.values().stream()
            .filter(ActivityModel::isGroup)
            .map(GroupModel.class::cast)
            .collect(toList());
    }

}
