package com.im.njams.sdk.configuration;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

import java.util.List;
import java.util.Map;

public interface ServerInstructionSettings {

    /**
     * @return the logMode
     */
    LogMode getLogMode();

    /**
     * @param logMode the logMode to set
     */
    void setLogMode(LogMode logMode);

    /**
     * @return the processes
     */
    Map<String, ProcessConfiguration> getProcesses();

    /**
     * @param processes the processes to set
     */
    void setProcesses(Map<String, ProcessConfiguration> processes);

    /**
     *
     * Return the ProcessSettings, which contains all settings for a Process.
     *
     * @param processPath ProcessPath to search for
     * @return The ProcessSettings if found, or null if no settings are
     * specified yet.
     */
    ProcessConfiguration getProcess(String processPath);

    /**
     * @return the dataMasking
     */
    List<String> getDataMasking();

    /**
     * @param dataMasking the dataMasking to set
     */
    void setDataMasking(List<String> dataMasking);

    /**
     * @return the recording
     */
    boolean isRecording();

    /**
     * @param recording the recording to set
     */
    void setRecording(boolean recording);
}
