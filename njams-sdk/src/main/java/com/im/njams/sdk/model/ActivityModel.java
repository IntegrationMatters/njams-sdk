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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlTransient;

import com.faizsiegeln.njams.messageformat.v4.common.SubProcess;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Activity;
import com.im.njams.sdk.common.IdUtil;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.ProcessConfiguration;

/**
 * This class represents a Activity when creating the initial Model.
 *
 * @author stkniep
 */
public class ActivityModel {

    private final ProcessModel processModel;

    private GroupModel parent;

    // fromActivityModelId -> TransitionModel
    private final Map<String, TransitionModel> incoming = new LinkedHashMap<>();
    // toActivityModelId -> TransitionModel
    private final Map<String, TransitionModel> outgoing = new LinkedHashMap<>();

    // this will be used for the svg rendering
    private int x;
    private int y;

    private boolean starter;

    private String id;
    private String name;
    private String type;
    private String config;
    private String mapping;

    // internal properties, shall no go to project message
    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * Create new ActivityModel
     *
     * @param processModel ProcessModel which should contain this ActivityModel
     */
    public ActivityModel(final ProcessModel processModel) {
        this.processModel = processModel;
    }

    /**
     * Create new ActivityModel
     *
     * @param processModel ProcessModel which should contain this ActivityModel
     * @param id           Id of this Activity
     * @param name         Name of this Activity
     * @param type         Type of this Activity
     */
    public ActivityModel(final ProcessModel processModel, final String id, final String name, final String type) {
        this(processModel);
        this.id = id;
        this.name = name;
        this.type = type;
    }

    final Activity getSerializableActivity(ProcessConfiguration processSettings) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setName(name);
        activity.setType(type);
        activity.setConfig(config);
        activity.setMapping(mapping);
        activity.setIsGroup(isGroup());
        if (parent != null) {
            activity.setParentId(parent.getId());
        }
        if (SubProcessActivityModel.class.isAssignableFrom(this.getClass())) {
            SubProcess sb = new SubProcess();
            sb.setName(((SubProcessActivityModel) this).getSubProcessName());
            if (((SubProcessActivityModel) this).getSubProcessPath() != null) {
                sb.setSubProcessPath(((SubProcessActivityModel) this).getSubProcessPath().toString());
            }
            activity.setSubProcess(sb);
        }
        if (processSettings != null) {
            ActivityConfiguration activityConfiguration = processSettings.getActivity(id);
            if (activityConfiguration != null) {
                activity.setTracePoint(activityConfiguration.getTracepoint());
                activity.setExtract(activityConfiguration.getExtract());
            }
        }
        return activity;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Adding a new incoming TransitionModel to the ActivityModel if it is not
     * already present. This ActivityModel will be assigned as toActivity on the
     * TransitionModel if not already assigned. If the fromActivitys is empty, a
     * Exception will be thrown.
     *
     * @param transitionModel to add
     */
    public void addIncomingTransition(final TransitionModel transitionModel) {
        if (!incoming.containsValue(transitionModel)) {
            if (transitionModel.getToActivity() != this) {
                transitionModel.setToActivity(this);
            }
            if (transitionModel.getFromActivity() == null) {
                throw new NjamsSdkRuntimeException("From activity on transition " + transitionModel.getId()
                        + " not set");
            }
            String fromId = transitionModel.getFromActivity().getId();
            if (incoming.containsKey(fromId) && incoming.get(fromId) != transitionModel) {
                throw new NjamsSdkRuntimeException("ActivityModel " + id
                        + " already contains a TransitionModel with fromActivity " + fromId + "!");
            }
            incoming.put(fromId, transitionModel);
        }
    }

    /**
     * Returns the connecting TransitionModel between this ActivityModel and the
     * given fromActivityModelId.
     *
     * @param fromActivityModelId to get incoming transition from
     * @return the {@link TransitionModel} or an Exception
     */
    public TransitionModel getIncomingTransitionFrom(String fromActivityModelId) {
        return incoming.get(fromActivityModelId);
    }

    /**
     * Adding a new outgoing TransitionModel to this ActivityModel if it is not
     * * already present. This ActivityModel will * be assigned as fromActivity
     * on the TransitionModel if not already assigned. If the toActivity is
     * empty, a Exception will be thrown.
     *
     * @param transitionModel to add
     */
    public void addOutgoingTransition(TransitionModel transitionModel) {
        if (!outgoing.containsValue(transitionModel)) {
            if (transitionModel.getFromActivity() != this) {
                transitionModel.setFromActivity(this);
            }
            if (transitionModel.getToActivity() == null) {
                throw new NjamsSdkRuntimeException("To activity on transition " + transitionModel.getId() + " not set");
            }
            String toId = transitionModel.getToActivity().getId();
            if (outgoing.containsKey(toId) && outgoing.get(toId) != transitionModel) {
                throw new NjamsSdkRuntimeException("ActivityModel " + id
                        + " already contains a TransitionModel with toActivity " + toId + "!");
            }
            outgoing.put(transitionModel.getToActivity().getId(), transitionModel);
        }
    }

    /**
     * Get horizontal position of the upper left corner of ActivityModel in
     * Diagram.
     *
     * @return the x position
     */
    @XmlTransient
    public int getX() {
        return x;
    }

    /**
     * Set horizontal position of the upper left corner of ActivityModel in
     * Diagram.
     *
     * @param x * the x to set
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Get vertical position of the upper left corner of ActivityModel in
     * Diagram.
     *
     * @return the y position
     */
    @XmlTransient
    public int getY() {
        return y;
    }

    /**
     * Set vertical position of the upper left corner of ActivityModel in
     * Diagram.
     *
     * @param y * the y to set
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * This function provides a forward chaining functionality to create a new *
     * ActivityModel with the given parameters. A TransitionModel which connects
     * the current with the new ActivityModel will be created automatically.
     *
     * @param toActivityModelId the ActivityModel to where the transition should
     *                          * point. It will be created if not already found
     * @param toActivityName    the name of the ActivityModel to where the
     *                          transition should point.
     * @param toActivityType    the type of the ActivityModel to where the
     *                          transition should point.
     * @return the new {@link ActivityModel}
     */
    public ActivityModel transitionTo(final String toActivityModelId, final String toActivityName,
            final String toActivityType) {
        if (getId().equals(toActivityModelId)) {
            throw new NjamsSdkRuntimeException("Destination must differ from source: " + getId());
        }
        ActivityModel to = processModel.getActivity(toActivityModelId);
        if (to == null) {
            to = new ActivityModel(processModel, toActivityModelId, toActivityName, toActivityType);
            processModel.addActivity(to);
        }
        TransitionModel transition = createTransition(to);
        checkIfPredecessorWasAChild(transition, to);
        return to;
    }

    /**
     * This function provides a forward chaining functionality to create a new
     * GroupModel with the given parameters. * A TransitionModel which connects
     * the current with the new ActivityModel will be created automatically.
     *
     * @param toGroupModelId the GroupModel to where the transition should *
     *                       point. It will be created if not already found
     * @param toGroupName    the name of the GroupModel to where the transition
     *                       should point.
     * @param toGroupType    the type of the GroupModel to where the transition
     *                       should point.
     * @return the new {@link ActivityModel}
     */
    public GroupModel
            transitionToGroup(final String toGroupModelId, final String toGroupName, final String toGroupType) {
        if (getId().equals(toGroupModelId)) {
            throw new NjamsSdkRuntimeException("Destination must differ from source: " + getId());
        }

        GroupModel to = processModel.getGroup(toGroupModelId);
        if (to == null) {
            to = new GroupModel(processModel, toGroupModelId, toGroupName, toGroupType);
            processModel.addActivity(to);
        }
        final TransitionModel transition = createTransition(to);
        checkIfPredecessorWasAChild(transition, to);
        return to;
    }

    /**
     * This function provides a forward chaining functionality to create a new
     * SubProcessModel with the given parameters. * A TransitionModel which
     * connects the current with the new ActivityModel will be created
     * automatically.
     *
     * @param toSubProcessModelId the GroupModel to where the transition should
     *                            * point. It will be created if not already found
     * @param toSubProcessName    the name of the GroupModel to where the
     *                            transition should point.
     * @param toSubProcessType    the type of the GroupModel to where the
     *                            transition should point.
     * @return the new {@link ActivityModel}
     */
    public SubProcessActivityModel transitionToSubProcess(final String toSubProcessModelId,
            final String toSubProcessName, final String toSubProcessType) {
        if (getId().equals(toSubProcessModelId)) {
            throw new NjamsSdkRuntimeException("Destination must differ from source: " + getId());
        }

        SubProcessActivityModel to = processModel.getSubProcess(toSubProcessModelId);
        if (to == null) {
            to = new SubProcessActivityModel(processModel, toSubProcessModelId, toSubProcessName, toSubProcessType);
            processModel.addActivity(to);
        }
        final TransitionModel transition = createTransition(to);
        checkIfPredecessorWasAChild(transition, to);
        return to;
    }

    private TransitionModel createTransition(ActivityModel to) {
        String transitionModelId = IdUtil.getTransitionModelId(getId(), to.getId());
        TransitionModel transition = new TransitionModel(processModel, transitionModelId, transitionModelId);
        transition.setFromActivity(this);
        transition.setToActivity(to);
        transition.linkObjects();
        processModel.addTransition(transition);
        return transition;
    }

    void setId(String id) {
        this.id = id;
    }

    void setName(String name) {
        this.name = name;
    }

    void setType(String type) {
        this.type = type;
    }

    /**
     * @return type of the activity
     */
    public String getType() {
        return type;
    }

    /**
     * @return name of the activity
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the configuration for the activity.
     *
     * @param config configuration
     */
    public void setConfig(String config) {
        this.config = config;
    }

    /**
     * Gets the configuration for the activity.
     *
     * @return configuration
     */
    public String getConfig() {
        return config;
    }

    /**
     * Sets the input mapping for the activity.
     *
     * @param mapping input mapping
     */
    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    /**
     * Gets the input mapping for the activity.
     *
     * @return input mapping
     */
    public String getMapping() {
        return mapping;
    }

    /**
     * @return the parent
     */
    public GroupModel getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(GroupModel parent) {
        if (this.parent == null) {
            this.parent = parent;
            parent.addChildActivity(this);
        } else if (this.parent != parent) {
            throw new NjamsSdkRuntimeException("This ActivityModel already belongs to a child");
        }
    }

    private void checkIfPredecessorWasAChild(TransitionModel transition, ActivityModel to) {
        if (parent != null) {
            parent.addChildTransition(transition);
            parent.addChildActivity(to);
        }
    }

    /**
     * @return the ProcessModel
     */
    public ProcessModel getProcessModel() {
        return processModel;
    }

    /**
     * @return all Predecessor activities
     */
    public List<ActivityModel> getPredecessors() {
        return Collections.unmodifiableList(incoming.values().stream().map(TransitionModel::getFromActivity)
                .collect(Collectors.toList()));
    }

    /**
     * @return all Successor activities
     */
    public List<ActivityModel> getSuccessors() {
        return Collections.unmodifiableList(outgoing.values().stream().map(TransitionModel::getToActivity)
                .collect(Collectors.toList()));
    }

    /**
     * @return the starter
     */
    public boolean isStarter() {
        return starter;
    }

    /**
     * @param starter set if this activity is a starter
     */
    public void setStarter(boolean starter) {
        this.starter = starter;
        if (starter) {
            if (parent != null) {
                parent.addStartActivity(this);
            } else {
                processModel.addStartActivity(this);
            }
        } else if (parent != null) {
            parent.removeStartActivity(this);
        } else {
            processModel.removeStartActivity(this);
        }
    }

    @Override
    public String toString() {
        return "ActivityModel{id=" + id + ", name=" + name + ", type=" + type + ", parent=" + parent + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(processModel);
        hash = 83 * hash + Objects.hashCode(id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ActivityModel other = (ActivityModel) obj;
        if (!Objects.equals(id, other.id)) {
            return false;
        }
        return Objects.equals(processModel, other.processModel);
    }

    /**
     * @return the isGroup
     */
    public final boolean isGroup() {
        return GroupModel.class.isAssignableFrom(this.getClass());
    }

    /**
     * Gets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key name of the property
     * @return Properties value of <b>null</b>
     */
    public Object getProperty(final String key) {
        return properties.get(key);
    }

    /**
     * Checks whether the activity has a property with a given name.
     *
     * @param key name of the property
     * @return <b>true</b> if and only if a property with the given name exists.
     */
    public boolean hasProperty(final String key) {
        return properties.containsKey(key);
    }

    /**
     * Sets a properties value. Properties will not be send within project
     * messages.
     *
     * @param key   name of the property
     * @param value value of the property
     */
    public void setProperty(final String key, final Object value) {
        properties.put(key, value);
    }
}
