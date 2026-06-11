/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Owns the optional-feature list and the container-mode flag of an {@link Njams} client.
 * The feature list is announced to the nJAMS server in the project message at start.
 * Obtain via {@code njams.features()}.
 */
public final class NjamsFeatures {

    private final LifecycleState lifecycle;
    private final List<Feature> features = new CopyOnWriteArrayList<>(Feature.INHERENT_FEATURES);
    private boolean containerMode = true;

    NjamsFeatures(LifecycleState lifecycle) {
        this.lifecycle = lifecycle;
    }

    /**
     * Returns the features of this client.
     *
     * @return copy of the current feature list
     */
    public List<Feature> list() {
        return new ArrayList<>(features);
    }

    /**
     * Adds a feature to the feature list. Features are announced to the nJAMS server in the
     * project message when the client starts.
     *
     * @param feature to add
     * @throws NjamsSdkRuntimeException if the client has already been started — a later change
     *                                  would never reach the server
     */
    public void add(Feature feature) {
        lifecycle.requireNotStarted("NjamsFeatures.add");
        addInternal(feature);
    }

    void addInternal(Feature feature) {
        if (!has(feature)) {
            features.add(feature);
        }
    }

    /**
     * Removes a feature from the feature list. Inherent SDK features cannot be removed.
     *
     * @param feature to remove
     * @throws NjamsSdkRuntimeException if the feature is inherent, or if the client has already
     *                                  been started — a later change would never reach the server
     */
    public void remove(Feature feature) {
        lifecycle.requireNotStarted("NjamsFeatures.remove");
        removeInternal(feature);
    }

    void removeInternal(Feature feature) {
        if (Feature.INHERENT_FEATURES.contains(feature)) {
            throw new NjamsSdkRuntimeException("Cannot remove inherent feature " + feature);
        }
        features.remove(feature);
    }

    /**
     * Returns whether the given feature is set.
     *
     * @param feature to check
     * @return true if present
     */
    public boolean has(Feature feature) {
        return features.contains(feature);
    }

    /**
     * Returns whether container-mode is enabled.
     *
     * @return true if container-mode is enabled
     */
    public boolean isContainerMode() {
        return containerMode;
    }

    /**
     * Enables or disables container-mode. Only allowed before the client is started.
     *
     * @param enabled true to enable
     */
    public void setContainerMode(boolean enabled) {
        if (lifecycle.isStarted()) {
            throw new NjamsSdkRuntimeException("Client is already started.");
        }
        containerMode = enabled;
        if (containerMode) {
            addInternal(Feature.CONTAINER_MODE);
        } else {
            removeInternal(Feature.CONTAINER_MODE);
        }
    }
}
