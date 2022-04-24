/*
 * Copyright (c) 2022 Faiz & Siegeln Software GmbH
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
