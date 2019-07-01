/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
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
package com.im.njams.sdk.configuration.boundary;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.common.Path;
import com.im.njams.sdk.configuration.control.ConfigurationProxyFactory;
import com.im.njams.sdk.configuration.control.JsonConfigurationProxy;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.logmessage.DataMasking;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Todo: Write Doc
 */
public class ConfigurationFacade {

    private static final String DEFAULT_CONFIGURATION_FACTORY = JsonConfigurationProxy.JSON_NAME;

    private ServerInstructionSettings configurationForNjams;

    public ConfigurationFacade(Properties properties){
        setDefaultNjamsServiceNameIfNeeded(properties);
        this.configurationForNjams = createConfigurationProxy(properties);
    }

    private void setDefaultNjamsServiceNameIfNeeded(Properties properties) {
        if(!properties.containsKey(ConfigurationProxyFactory.CONFIGURATION_PROXY_SERVICE)){
            properties.setProperty(ConfigurationProxyFactory.CONFIGURATION_PROXY_SERVICE, DEFAULT_CONFIGURATION_FACTORY);
        }
    }

    private ServerInstructionSettings createConfigurationProxy(Properties properties) {
        ConfigurationProxyFactory configurationProxyFactory = new ConfigurationProxyFactory(properties);
        return configurationProxyFactory.getInstance();
    }

    public void start(){
        ((ConfigurationProxy)configurationForNjams).loadConfiguration();
        loadDataMaskingFromConfiguration(configurationForNjams);
    }

    //For Datamasking
    /**
     * Initialize the datamasking feature
     */
    private void loadDataMaskingFromConfiguration(ServerInstructionSettings configuration) {
        DataMasking.addPatterns(configuration.getDataMasking());
    }

    //For ServerInstructionSettings
    public LogMode getLogModeFromConfiguration() {
        return configurationForNjams.getLogMode();
    }

    public void setLogModeToConfiguration(LogMode logMode) {
        configurationForNjams.setLogMode(logMode);
    }

    public Map<String, ProcessConfiguration> getProcessesFromConfiguration() {
        return configurationForNjams.getProcesses();
    }

    public void setProcessesToConfiguration(Map<String, ProcessConfiguration> processes) {
        configurationForNjams.setProcesses(processes);
    }

    public ProcessConfiguration getProcessFromConfiguration(String processPath) {
        return configurationForNjams.getProcess(processPath);
    }

    public List<String> getDataMaskingFromConfiguration() {
        return configurationForNjams.getDataMasking();
    }

    public void setDataMaskingToConfiguration(List<String> dataMasking) {
        configurationForNjams.setDataMasking(dataMasking);
    }

    public boolean isConfigurationRecording() {
        return configurationForNjams.isRecording();
    }

    public void setRecordingToConfiguration(boolean recording) {
        configurationForNjams.setRecording(recording);
    }

    /**
     * Returns if the given process is excluded. This could be explicitly set on
     * the process, or if the Engine LogMode is set to none.
     *
     * @param processPath for the process which should be checked
     * @return true if the process is excluded, or false if not
     */
    public boolean isProcessExcluded(Path processPath) {
        if (processPath == null) {
            return false;
        }
        if (configurationForNjams.getLogMode() == LogMode.NONE) {
            return true;
        }
        ProcessConfiguration processConfiguration = configurationForNjams.getProcess(processPath.toString());
        return processConfiguration != null && processConfiguration.isExclude();
    }

    //For ConfigurationProxy
    public void loadConfigurationFromStorageInMemory(){
        ((ConfigurationProxy)configurationForNjams).loadConfiguration();
    }

    public void saveConfigurationFromMemoryToStorage(){
        ((ConfigurationProxy)configurationForNjams).saveConfiguration();
    }
}
