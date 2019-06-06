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
package com.im.njams.sdk.configuration.control;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.configuration.boundary.ConfigurationProxy;
import com.im.njams.sdk.configuration.entity.Configuration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * ConfigurationProvider implementation which holds the configuration in memory.
 * Therefore, if this ConfigurationProvider is used, all values will be lost
 * after restart.
 *
 * @author pnientiedt
 */
public class MemoryConfigurationProxy implements ConfigurationProxy {

    private static final String PROPERTY_PREFIX = "njams.sdk.configuration.memory";

    private static final String MEMORY_NAME = "memory";

    protected Properties properties;

    protected Configuration realConfiguration;

    //NjamsService
    /**
     * Returns the value {@value #MEMORY_NAME} as name for this ConfigurationProxy
     *
     * @return the name of this ConfigurationProxy
     */
    @Override
    public String getName() {
        return MEMORY_NAME;
    }

    //ServerInstructionSettings
    @Override
    public LogMode getLogMode() {
        return realConfiguration.getLogMode();
    }

    @Override
    public void setLogMode(LogMode logMode) {
        realConfiguration.setLogMode(logMode);
    }

    @Override
    public Map<String, ProcessConfiguration> getProcesses() {
        return realConfiguration.getProcesses();
    }

    @Override
    public void setProcesses(Map<String, ProcessConfiguration> processes) {
        realConfiguration.setProcesses(processes);
    }

    @Override
    public ProcessConfiguration getProcess(String processPath) {
        return realConfiguration.getProcess(processPath);
    }

    @Override
    public List<String> getDataMasking() {
        return realConfiguration.getDataMasking();
    }

    @Override
    public void setDataMasking(List<String> dataMasking) {
        realConfiguration.setDataMasking(dataMasking);
    }

    @Override
    public boolean isRecording() {
        return realConfiguration.isRecording();
    }

    @Override
    public void setRecording(boolean recording) {
        realConfiguration.setRecording(recording);
    }

    //ConfigurationProxy
    /**
     * This does nothing because there is nothing to configure on this simple
     * ConfigurationProvider implementation
     *
     * @param properties Properties for configuration
     */
    @Override
    public void configure(Properties properties) {
        this.properties = properties;
    }

    /**
     * It creates a possibly overrides the in Configuration in memory by a new Configuration.
     */
    @Override
    public void loadConfiguration() {
        this.realConfiguration = new Configuration();
    }

    /**
     * This method does nothing, because the MemoryConfigurationProxy has no underlying storage.
     */
    @Override
    public void saveConfiguration() {
        //Does nothing because it is only saved in memory.
    }

    /**
     * Returns the prefix for the MemoryConfigurationProvider. Only properties
     * starting with this prefix will be used for initialization.
     *
     * @return the prefix
     */
    @Override
    public String getPropertyPrefix() {
        return PROPERTY_PREFIX;
    }
}
