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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Transition;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Class which represents a Transition in the current model. A Transition is
 * needed to connect two Activities
 *
 * @author pnientiedt
 */
public class TransitionModel {

    private final ProcessModel processModel;

    private GroupModel parent;

    // the from activity
    private ActivityModel fromActivity;

    // the to activity
    private ActivityModel toActivity;

    private String id;
    private String name;
    private String config;

    // internal properties, shall no go to project message
    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * Create a new TransitionModel
     *
     * @param processModel ProcessModel which should contain this
     * TransitionModel
     * @param id Id of this TransitionModel
     * @param name Name of this TransitionModel
     */
    public TransitionModel(ProcessModel processModel, String id, String name) {
        this.processModel = processModel;
        this.id = id;
        this.name = name;
    }

    Transition getSerializableTransition() {
        Transition transition = new Transition();
        transition.setId(id);
        transition.setName(name);
        transition.setConfig(config);
        transition.setFrom(fromActivity.getId());
        transition.setTo(toActivity.getId());
        return transition;
    }

    /**
     * Get the {@link ActivityModel} from where Transition starts.
     *
     * @return the from {@link ActivityModel}
     */
    @XmlTransient
    public ActivityModel getFromActivity() {
        return fromActivity;
    }

    /**
     * Set from Activity by providing the ActivityModel.
     *
     * @param fromActivity the fromActivity to set
     */
    public void setFromActivity(ActivityModel fromActivity) {
        this.fromActivity = fromActivity;
    }

    /**
     * Get the {@link ActivityModel} where Transition ends.
     *
     * @return the {@link ActivityModel}
     */
    @XmlTransient
    public ActivityModel getToActivity() {
        return toActivity;
    }

    /**
     * Set to Activity by providing the ActivityModel.
     *
     * @param toActivity the toActivity to set
     */
    public void setToActivity(ActivityModel toActivity) {
        this.toActivity = toActivity;
    }

    /**
     * If the fromActivity and the toActivity is not null, link the objects, by
     * adding this ModelTransition as outgoing transition to the fromActivity,
     * and as incomming transition to the toActivity.
     */
    void linkObjects() {
        if (fromActivity == null) {
            throw new NjamsSdkRuntimeException("fromActivity not set");
        }
        if (toActivity == null) {
            throw new NjamsSdkRuntimeException("toActivity not set");
        }
        fromActivity.addOutgoingTransition(this);
        toActivity.addIncomingTransition(this);
    }

    /**
     *
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the configuration for the transition.
     *
     * @param config configuration
     */
    public void setConfig(String config) {
        this.config = config;
    }

    /**
     * Gets the configuration for the transition.
     *
     * @return configuration
     */
    public String getConfig() {
        return config;
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
            parent.addChildTransition(this);
        } else if (this.parent != parent) {
            throw new NjamsSdkRuntimeException("This ActivityModel already belongs to a child");
        }
    }

    @Override
    public String toString() {
        return "TransitionModel{" + "fromActivity=" + fromActivity
                + ", toActivity=" + toActivity + ", id=" + id + ", name=" + name + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + Objects.hashCode(this.processModel);
        hash = 43 * hash + Objects.hashCode(this.id);
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
        final TransitionModel other = (TransitionModel) obj;
        return Objects.equals(this.id, other.id);
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
     * Checks whether the transsition has a property with a given name.
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
     * @param key name of the property
     * @param value value of the property
     */
    public void setProperty(final String key, final Object value) {
        properties.put(key, value);
    }
}
