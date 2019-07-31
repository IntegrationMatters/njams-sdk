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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionWriterTemplate;
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

    @Override
    protected ConditionParameter[] getNeededParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void configureCondition() throws NjamsInstructionException {

        setLogLevelToCondition();

        setExcludedToCondition();

        setLogModeToCondition();

    }

    private void setLogLevelToCondition() throws NjamsInstructionException {

        LogLevel logLevelToSet = getConditionRequestReader().getLogLevel();

        ProcessConfiguration processCondition = getClientCondition().getOrCreateProcessCondition();

        processCondition.setLogLevel(logLevelToSet);

    }

    private void setExcludedToCondition() {

        boolean isExclude = getConditionRequestReader().getExcluded();

        ProcessConfiguration processCondition = getClientCondition().getOrCreateProcessCondition();

        processCondition.setExclude(isExclude);
    }

    private void setLogModeToCondition() {

        LogMode optionalLogMode = getLogMode();

        if (optionalLogMode != null) {

            Njams condition = getClientCondition().getCondition();

            condition.setLogModeToConfiguration(optionalLogMode);

        }
    }

    private LogMode getLogMode() {
        LogMode toReturn = null;
        try {
            toReturn = getConditionRequestReader().getLogMode();
        } catch (NjamsInstructionException e) {
            //Do nothing
        }
        return toReturn;
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            ConditionRequestReader requestReader = getConditionRequestReader();
            LOG.debug("Set LogLevel for {}.", requestReader.getProcessPath());
        }
    }
}
