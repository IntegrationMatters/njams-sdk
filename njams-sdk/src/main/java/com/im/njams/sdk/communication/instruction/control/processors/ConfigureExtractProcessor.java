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
package com.im.njams.sdk.communication.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionConstants;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.instruction.control.templates.condition.ConditionWriterTemplate;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class ConfigureExtractProcessor extends ConditionWriterTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigureExtractProcessor.class);

    private static final String[] neededParameter =
            new String[]{ConditionConstants.PROCESS_PATH_KEY, ConditionConstants.ACTIVITY_ID_KEY,
                    ConditionConstants.EXTRACT_KEY};

    public ConfigureExtractProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected String[] getEssentialParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void configureCondition() throws NjamsInstructionException {
        Extract extractOfRequest = requestReader.getExtract();

        ActivityConfiguration activityCondition = conditionFacade.getOrCreateActivityCondition();

        activityCondition.setExtract(extractOfRequest);
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Configured Extract for {}#{}.", requestReader.getProcessPath(), requestReader.getActivityId());
        }
    }
}
