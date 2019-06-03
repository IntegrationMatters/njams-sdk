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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The configuration contains all configuration of the client, which are not part of the
 * initial settings, and therefore could be changed by the client instance,
 * or via instructions.
 *
 * @author pnientiedt
 */
public class Configuration {

    @JsonIgnore
    private ConfigurationProvider configurationProvider;

    private LogMode logMode = LogMode.COMPLETE;
    private Map<String, ProcessConfiguration> processes = new HashMap<>();
    private List<String> dataMasking = new ArrayList<>();
    private boolean recording = true;

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
     * Return the ProcessSettings, which contains all settings for a Process.
     *
     * @param processPath ProcessPath to search for
     * @return The ProcessSettings if found, or null if no settings are
     * specified yet.
     */
    public ProcessConfiguration getProcess(String processPath) {
        return processes.get(processPath);
    }

    /**
     * @return the dataMasking
     */
    public List<String> getDataMasking() {
        return dataMasking;
    }

    /**
     * @param dataMasking the dataMasking to set
     */
    public void setDataMasking(List<String> dataMasking) {
        this.dataMasking = dataMasking;
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
