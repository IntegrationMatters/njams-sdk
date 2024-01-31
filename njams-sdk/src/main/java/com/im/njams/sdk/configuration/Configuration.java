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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.settings.Settings;

/**
 * The configuration contains all configuration of the client, which are not part of the
 * initial settings, and therefore could be changed by the client instance,
 * or via instructions.
 *
 * @author pnientiedt
 */
public class Configuration {
    // this value is used only for initialization
    private static boolean bootstrapRecording = true;

    @JsonIgnore
    private ConfigurationProvider configurationProvider;

    private LogMode logMode = LogMode.COMPLETE;
    private Map<String, ProcessConfiguration> processes = new ConcurrentHashMap<>();

    /**
     * Should be provided by the Settings
     */
    @Deprecated
    private List<String> dataMasking = new ArrayList<>();
    private Boolean recording = null;

    private final ProcessFilter processFilter = new ProcessFilter(this);
    private Collection<ProcessFilterEntry> processFilters = new ArrayList<>();

    /**
     * Set a default value for {@link #isRecording()} which is used only as default when creating a new configuration.
     * @param enabled When recording is should be enabled.
     */
    public static void setRecordingBootstrapValue(boolean enabled) {
        bootstrapRecording = enabled;
    }

    /**
     * @param configurationProvider to be set
     */
    public void setConfigurationProvider(ConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    /**
     * @return ConfigurationProvider
     */
    public ConfigurationProvider getConfigurationProvider() {
        return configurationProvider;
    }

    /**
     * Save the configuration via the configured ConfigurationProvider
     */
    public void save() {
        configurationProvider.saveConfiguration(this);
    }

    /**
     * @return the logMode
     */
    public LogMode getLogMode() {
        return logMode;
    }

    /**
     * @param logMode the logMode to set
     */
    public void setLogMode(LogMode logMode) {
        this.logMode = logMode;
    }

    /**
     * @return the processes
     */
    public Map<String, ProcessConfiguration> getProcesses() {
        return processes;
    }

    /**
     * @param processes the processes to set
     */
    public void setProcesses(Map<String, ProcessConfiguration> processes) {
        this.processes = processes;
    }

    /**
     *
     * Return the {@link ProcessConfiguration}, which contains all settings for a process.
     *
     * @param processPath The path of the process for that a configuration shall be returned.
     * @return Always ProcessSettings for the given path, which is created if not exists. Use {@link #hasProcess(Path)}
     * to check whether a configuration exists.
     */
    public ProcessConfiguration getProcess(String processPath) {
        ProcessConfiguration process = processes.get(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            process.setRecording(isRecording());
            processes.put(processPath, process);
        }
        return process;
    }

    /**
    *
    * Return the {@link ProcessConfiguration}, which contains all settings for a process.
    *
    * @param processPath The path of the process for that a configuration shall be returned.
    * @return Always ProcessSettings for the given path, which is created if not exists. Use {@link #hasProcess(Path)}
    * to check whether a configuration exists.
    */
    public ProcessConfiguration getProcess(Path processPath) {
        return getProcess(processPath.toString());
    }

    /**
     * Returns whether or not this configuration contains a separate {@link ProcessConfiguration} for the process with
     * the given path.
     * @param processPath The path of the process to check for a configuration.
     * @return <code>true</code> if there is a configuration for the given path.
     */
    public boolean hasProcess(Path processPath) {
        return processes.containsKey(processPath.toString());
    }

    /**
     * @deprecated Should be provided by the {@link Settings}
     * @return the dataMasking
     */
    @Deprecated
    public List<String> getDataMasking() {
        return dataMasking;
    }

    /**
     * @deprecated Should be provided by the {@link Settings}
     *
     * @param dataMasking the dataMasking to set
     */
    @Deprecated
    public void setDataMasking(List<String> dataMasking) {
        this.dataMasking = dataMasking;
    }

    /**
     * @return the recording
     */
    public boolean isRecording() {
        return recording != null ? recording : bootstrapRecording;
    }

    /**
     * @param recording the recording to set
     */
    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    /**
     * @return List of all defined process (-path) filters.
     */
    public Collection<ProcessFilterEntry> getProcessFilters() {
        return processFilters;
    }

    /**
     * Sets (overwrites) all process (-path) filters.
     * @param processFilters New filters to set
     */
    public void setProcessFilters(Collection<ProcessFilterEntry> processFilters) {
        this.processFilters = processFilters;
    }

    /**
     * Adds a process filter to the current list of filters.
     * @param processFilter The filter to add.
     */
    public void addProcessFilter(ProcessFilterEntry processFilter) {
        processFilters.add(processFilter);
    }

    /**
     * Returns <code>true</code> if the given process is excluded from processing for any reason.
     * @param processPath The process path to test
     * @return <code>true</code> if the process must not be processed.
     * @see ProcessFilter#isSelected(Path)
     */
    public boolean isProcessExcluded(Path processPath) {
        return !processFilter.isSelected(processPath);
    }

    /**
     * Returns <code>true</code> only if there is a specific exclude setting for given process.<br>
     * This is not the same as {@link #isProcessExcluded(Path)} since there may be additional filters that lead to
     * excluding this process.<br>
     * This method is mainly for processing configuration commands from nJAMS server.
     * @param processPath The process path to test
     * @return Whether there is a specific exclude filter for the given process.
     * @see ProcessFilter#hasExcludeFilter(Path)
     */
    public boolean hasProcessExcludeFilter(Path processPath) {
        return processFilter.hasExcludeFilter(processPath);
    }

    /**
     * Sets an exclude filter for the given process, or removes it according to the given <code>exclude</code>
     * parameter.
     * @param processPath The process path for that an exclude shall be updated
     * @param excluded If <code>true</code>, an exclude filter is set, otherwise it's removed.
     * @see ProcessFilter#setExcluded(Path, boolean)
     */
    public void setProcessExcluded(Path processPath, boolean excluded) {
        processFilter.setExcluded(processPath, excluded);
    }
}
