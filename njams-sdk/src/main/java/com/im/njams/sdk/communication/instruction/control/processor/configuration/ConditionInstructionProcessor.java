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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.communication.instruction.control.processor.AbstractInstructionProcessor;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

import java.util.List;

public abstract class ConditionInstructionProcessor extends AbstractInstructionProcessor<ConditionInstruction> {

    private static final String UNABLE_TO_SAVE_CONFIGURATION = "Unable to save configuration";

    protected Njams njams;

    public ConditionInstructionProcessor(Njams njams) {
        this.njams = njams;
    }

    @Override
    protected boolean prepareProcessing() {
        clear();
        ConditionParameter[] neededParametersForProcessing = getNeededParametersForProcessing();
        List<ConditionParameter> missingParameters = getInstruction().getRequestReader()
                .searchForMissingParameters(neededParametersForProcessing);
        boolean wereAllParametersSet = SUCCESS;
        if (!missingParameters.isEmpty()) {
            wereAllParametersSet = FAILURE;
        }
        return wereAllParametersSet;
    }

    protected abstract void clear();

    protected abstract ConditionParameter[] getNeededParametersForProcessing();

    protected ProcessConfiguration getOrCreateProcessConfigurationFor(String processPath) {
        ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            njams.getProcessesFromConfiguration().put(processPath, process);
        }
        return process;
    }

    protected ActivityConfiguration getOrCreateActivityConfigurationFromProcessFor(ProcessConfiguration process,
            String activityId) {
        ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            activity = new ActivityConfiguration();
            process.getActivities().put(activityId, activity);
        }
        return activity;
    }

    protected void saveConfiguration() {
        try {
            njams.saveConfigurationFromMemoryToStorage();
        } catch (final Exception e) {
            getInstruction().getResponseWriter().
                    setResultCode(ResponseWriter.ResultCode.WARNING).
                    setResultMessage(UNABLE_TO_SAVE_CONFIGURATION + (e != null ? ": " + e.getMessage() : ""));
        }
    }
}
