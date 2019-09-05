/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.condition;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;

public class ConditionProxy {

    private static final String UNABLE_TO_SAVE_CONFIGURATION = "Unable to save configuration";

    private Njams condition;

    private String processPath;

    private String activityId;

    public ConditionProxy(Njams condition) {
        this.condition = condition;
    }

    public void setProcessPath(String processPath) {
        this.processPath = processPath;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public Njams getCondition() {
        return condition;
    }

    public ProcessConfiguration getProcessCondition() throws NjamsInstructionException {
        ProcessConfiguration process = condition.getProcessFromConfiguration(processPath);
        if (process == null) {
            throw new NjamsInstructionException("Condition of process \"" + processPath + "\" not found");
        }
        return process;
    }

    public ActivityConfiguration getActivityCondition() throws NjamsInstructionException {
        ProcessConfiguration processConfiguration = getProcessCondition();
        ActivityConfiguration activity = processConfiguration.getActivity(activityId);

        if (activity == null) {
            throw new NjamsInstructionException(
                    "Condition of activity \"" + activityId + "\" on process \"" + processPath + "\" not found");
        }
        return activity;
    }

    public Extract getExtract() throws NjamsInstructionException {
        ActivityConfiguration activityCondition = getActivityCondition();

        final Extract extract = activityCondition.getExtract();
        if (extract == null) {
            throw new NjamsInstructionException(
                    "Extract for activity \"" + activityId + "\" on process \"" + processPath + "\" not found");
        }
        return extract;
    }

    public TracepointExt getTracePoint() throws NjamsInstructionException {
        ActivityConfiguration activityCondition = getActivityCondition();

        TracepointExt tracePoint = activityCondition.getTracepoint();
        if (tracePoint == null) {
            throw new NjamsInstructionException(
                    "Tracepoint for activity \"" + activityId + "\" on process \"" + processPath + "\" not found");
        }
        return tracePoint;
    }

    public LogLevel getLogLevel() throws NjamsInstructionException {
        ProcessConfiguration processCondition = getProcessCondition();
        return processCondition.getLogLevel();
    }

    public boolean isExcluded() throws NjamsInstructionException {
        ProcessConfiguration processCondition = getProcessCondition();
        return processCondition.isExclude();
    }

    public ProcessConfiguration getOrCreateProcessCondition() {
        ProcessConfiguration processCondition;
        try {
            processCondition = getProcessCondition();
        } catch (NjamsInstructionException processNotFoundException) {
            processCondition = new ProcessConfiguration();
            condition.getProcessesFromConfiguration().put(processPath, processCondition);
        }
        return processCondition;
    }

    public ActivityConfiguration getOrCreateActivityCondition() {
        ProcessConfiguration processCondition = getOrCreateProcessCondition();
        ActivityConfiguration activityCondition;
        try {
            activityCondition = getActivityCondition();
        } catch (NjamsInstructionException activityNotFoundException) {
            activityCondition = new ActivityConfiguration();
            processCondition.getActivities().put(activityId, activityCondition);
        }
        return activityCondition;
    }

    public void saveCondition() throws NjamsInstructionException {
        try {
            condition.saveConfigurationFromMemoryToStorage();
        } catch (final RuntimeException e) {
            throw new NjamsInstructionException(UNABLE_TO_SAVE_CONFIGURATION, e);
        }
    }

    public LogMode getLogMode() {
        return condition.getLogModeFromConfiguration();
    }
}
