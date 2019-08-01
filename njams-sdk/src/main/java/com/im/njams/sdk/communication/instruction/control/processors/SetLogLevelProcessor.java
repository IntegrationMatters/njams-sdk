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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.instruction.control.templates.condition.ConditionWriterTemplate;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class SetLogLevelProcessor extends ConditionWriterTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(SetLogLevelProcessor.class);

    private static final ConditionParameter[] neededParameter =
            new ConditionParameter[]{ConditionParameter.PROCESS_PATH, ConditionParameter.LOG_LEVEL};

    public SetLogLevelProcessor(Njams njams) {
        super(njams);
    }

    private LogLevel logLevelToSet;

    private boolean excludedToSet;

    private LogMode logModeToSet;

    @Override
    protected ConditionParameter[] getEssentialParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void configureCondition() throws NjamsInstructionException {

        logLevelToSet = getLogLevelFromRequest();
        excludedToSet = getExcludedFromRequest();
        logModeToSet = getLogModeFromRequest();

        setExtractedParametersToProcessCondition();

        setLogModeToCondition();
    }

    private void setExtractedParametersToProcessCondition() {
        ProcessConfiguration processCondition = conditionFacade.getOrCreateProcessCondition();
        processCondition.setLogLevel(logLevelToSet);
        processCondition.setExclude(excludedToSet);
    }

    private LogLevel getLogLevelFromRequest() throws NjamsInstructionException {
        return requestReader.getLogLevel();
    }

    private boolean getExcludedFromRequest() {
        return requestReader.getExcluded();
    }

    private LogMode getLogModeFromRequest() {
        try {
            return requestReader.getLogMode();
        } catch (NjamsInstructionException e) {
            return null;
        }
    }

    private void setLogModeToCondition() {
        if (logModeToSet != null) {
            conditionFacade.getCondition().setLogModeToConfiguration(logModeToSet);

        }
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Set LogLevel for {}.", requestReader.getProcessPath());
        }
    }
}
