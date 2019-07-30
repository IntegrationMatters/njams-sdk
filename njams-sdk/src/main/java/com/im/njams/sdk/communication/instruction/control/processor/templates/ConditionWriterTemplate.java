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

package com.im.njams.sdk.communication.instruction.control.processor.templates;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConditionWriterTemplate extends ConditionReaderTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ConditionWriterTemplate.class);

    private static final String UNABLE_TO_SAVE_CONFIGURATION = "Unable to save configuration";

    public ConditionWriterTemplate(Njams njams) {
        super(njams);
    }

    @Override
    public void processConditionInstruction() throws NjamsInstructionException {
        configureCondition();

        saveConfiguration();
    }

    protected abstract void configureCondition() throws NjamsInstructionException;

    void saveConfiguration() throws NjamsInstructionException {
        try {
            getClientCondition().saveConfigurationFromMemoryToStorage();
        } catch (final RuntimeException e) {
            throw new NjamsInstructionException(UNABLE_TO_SAVE_CONFIGURATION, e);
        }
    }

    public ProcessConfiguration getOrCreateProcessCondition() {
        ProcessConfiguration processCondition;
        try {
            processCondition = getProcessCondition();
        } catch (NjamsInstructionException processNotFoundException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(processNotFoundException.getMessage());
            }
            processCondition = new ProcessConfiguration();
            getClientCondition().getProcessesFromConfiguration()
                    .put(getConditionRequestReader().getProcessPath(), processCondition);
        }
        return processCondition;
    }

    public ActivityConfiguration getOrCreateActivityCondition() {
        ProcessConfiguration processCondition = getOrCreateProcessCondition();
        ActivityConfiguration activityCondition;
        try {
            activityCondition = getActivityCondition();
        } catch (NjamsInstructionException activityNotFoundException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(activityNotFoundException.getMessage());
            }
            activityCondition = new ActivityConfiguration();
            processCondition.getActivities().put(getConditionRequestReader().getActivityId(), activityCondition);
        }
        return activityCondition;
    }
}
