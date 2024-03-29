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
package com.im.njams.sdk.configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;

/**
 * Settings container for processes.
 *
 * @author pnientiedt
 */
public class ProcessConfiguration {

    private LogLevel logLevel = LogLevel.INFO;
    @Deprecated(since = "5.0.0", forRemoval = true)
    private Boolean exclude = false;
    private Map<String, ActivityConfiguration> activities = new ConcurrentHashMap<>();
    private boolean recording = true;

    /**
     * Use {@link Configuration#getProcess(com.im.njams.sdk.common.Path)} for creating an instance.
     */
    public ProcessConfiguration() {
        // instances created by Configuration class
    }

    /**
     * @return the logLevel
     */
    public LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * @param logLevel the logLevel to set
     */
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * @deprecated Replaced by {@link Configuration#hasProcessExcludeFilter(com.im.njams.sdk.common.Path)}
     * @return the exclude
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public Boolean isExclude() {
        return exclude;
    }

    /**
     * @deprecated Replaced by {@link Configuration#setProcessExcluded(com.im.njams.sdk.common.Path, boolean)}
     * @param exclude the exclude to set
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public void setExclude(Boolean exclude) {
        this.exclude = exclude;
    }

    /**
     * @return the activities
     */
    public Map<String, ActivityConfiguration> getActivities() {
        return activities;
    }

    /**
     * @param activities the activities to set
     */
    public void setActivities(Map<String, ActivityConfiguration> activities) {
        this.activities = activities;
    }

    /**
     * Returns the {@link ActivityConfiguration} for a given activity model id
     *
     * @param activityId for the activity
     * @return The configuration if exists, otherwise <code>null</code>.
     */
    public ActivityConfiguration getActivity(String activityId) {
        return activities.get(activityId);
    }

    /**
     * Returns the {@link ActivityConfiguration} for a given activity model id.
     * If no such configuration exists, this method will create one.
     *
     * @param activityId for the activity
     * @return Always a configuration for the given model-id, otherwise <code>null</code>.
     */
    public ActivityConfiguration getOrCreateActivity(String activityId) {
        return activities.computeIfAbsent(activityId, k -> new ActivityConfiguration());
    }

    /**
     * @return the recording
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * @param recording the recording to set
     */
    public void setRecording(boolean recording) {
        this.recording = recording;
    }
}
