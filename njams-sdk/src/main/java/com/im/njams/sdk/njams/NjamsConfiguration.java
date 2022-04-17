package com.im.njams.sdk.njams;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.client.CleanTracepointsTask;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.njams.metadata.NjamsMetadata;
import com.im.njams.sdk.settings.Settings;

import java.util.Map;

public class NjamsConfiguration {

    private final NjamsMetadata njamsMetadata;
    private final NjamsSender njamsSender;
    private final Settings njamsSettings;
    private final Configuration configuration;

    public NjamsConfiguration(Configuration configuration, NjamsMetadata njamsMetadata, NjamsSender njamsSender, Settings settings) {
        this.njamsMetadata = njamsMetadata;
        this.njamsSender = njamsSender;
        this.njamsSettings = settings;
        this.configuration = configuration;
    }

    public void start() {
        NjamsDataMasking.start(njamsSettings, configuration);
        CleanTracepointsTask.start(njamsMetadata, configuration, njamsSender);
    }

    public void stop() {
        CleanTracepointsTask.stop(njamsMetadata);
    }

    public LogMode getLogMode() {
        return configuration.getLogMode();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public ProcessConfiguration getProcess(String processPath) {
        return configuration.getProcess(processPath);
    }

    public Map<String, ProcessConfiguration> getProcesses() {
        return configuration.getProcesses();
    }

    public void setLogMode(LogMode logMode) {
        configuration.setLogMode(logMode);
    }

    public void setEngineWideRecording(boolean engineWideRecording) {
        configuration.setRecording(engineWideRecording);
    }

    public void save() {
        configuration.save();
    }

    public boolean isRecording() {
        return configuration.isRecording();
    }
}
