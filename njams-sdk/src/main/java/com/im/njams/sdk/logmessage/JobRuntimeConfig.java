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
package com.im.njams.sdk.logmessage;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Per-job snapshot of the server-driven runtime configuration (log mode, log level,
 * exclusion, recording) plus tracepoint and activity-configuration lookups.
 */
final class JobRuntimeConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JobRuntimeConfig.class);

    final LogMode logMode;
    final LogLevel logLevel;
    final boolean exclude;
    final boolean recording;
    /** Matches the legacy early-return: the recorded-attribute is only added when a configuration was present. */
    final boolean addRecordedAttribute;

    /**
     * Initializes the processConfiguration and the activityConfigurations.
     */
    JobRuntimeConfig(ProcessModel processModel) {
        LogMode mode = LogMode.COMPLETE;
        LogLevel level = LogLevel.INFO;
        boolean excluded = false;
        boolean record = true;
        Configuration configuration = processModel.getNjams().getConfiguration();
        if (configuration == null) {
            LOG.error("Unable to set LogMode, LogLevel and Exclude for {}, configuration is null",
                    processModel.getPath());
        } else {
            mode = configuration.getLogMode();
            LOG.debug("Set LogMode for {} to {}", processModel.getPath(), mode);

            record = configuration.isRecording();
            LOG.debug("Set recording for {} to {} based on client settings {}",
                    processModel.getPath(), record, configuration.isRecording());

            ProcessConfiguration process = configuration.getProcess(processModel.getPath().toString());
            if (process != null) {
                level = process.getLogLevel();
                LOG.debug("Set LogLevel for {} to {}", processModel.getPath(), level);
                record = process.isRecording();
                LOG.debug("Set recording for {} to {} based on process settings {} and client setting {}",
                        processModel.getPath(), record, process.isRecording(), configuration.isRecording());
            }
            excluded = processModel.getNjams().isExcluded(processModel.getPath());
            LOG.debug("Set Exclude for {} to {}", processModel.getPath(), excluded);
        }
        logMode = mode;
        logLevel = level;
        exclude = excluded;
        recording = record;
        addRecordedAttribute = configuration != null && record;
    }

    /**
     * Returns <code>true</code> if the given tracepoint configuration is currently active.
     *
     * @param tracepoint The tracepoint to check
     * @return <code>true</code> if the given tracepoint configuration is currently active.
     */
    boolean isActiveTracepoint(TracepointExt tracepoint) {
        if (tracepoint != null) {
            //if tracepoint exists, check timings
            LocalDateTime now = DateTimeUtility.now();
            //timing is right, and iterations are less than configured
            return !now.isBefore(tracepoint.getStarttime()) && now.isBefore(tracepoint.getEndtime())
                    && !tracepoint.iterationsExceeded();
        }
        return false;
    }

    /**
     * Returns the runtime configuration for a specific {@link ActivityModel} if any.
     *
     * @param activityModel The model for that configuration shall be returned.
     * @return May be <code>null</code> if no configuration exists.
     */
    ActivityConfiguration getActivityConfiguration(ActivityModel activityModel) {
        if (activityModel == null) {
            return null;
        }
        ProcessModel processModel = activityModel.getProcessModel();
        if (processModel == null) {
            return null;
        }
        Configuration configuration = processModel.getNjams().getConfiguration();
        if (configuration == null) {
            return null;
        }
        ProcessConfiguration processConfig = configuration.getProcess(processModel.getPath().toString());
        if (processConfig == null) {
            return null;
        }
        return processConfig.getActivity(activityModel.getId());
    }
}
