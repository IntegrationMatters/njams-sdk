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
import com.im.njams.sdk.communication.instruction.control.templates.condition.ConditionReaderTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class GetLogLevelProcessor extends ConditionReaderTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogLevelProcessor.class);

    private static final ConditionParameter[] neededParameter =
            new ConditionParameter[]{ConditionParameter.PROCESS_PATH};

    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;

    private static final boolean DEFAULT_IS_EXCLUDED = false;

    public GetLogLevelProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected ConditionParameter[] getEssentialParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void processConditionInstruction() {

        final LogLevel logLevel = getLogLevel();
        final boolean isExclude = isExclude();
        final LogMode logMode = getLogMode();

        responseWriter.
                setLogLevel(logLevel).
                setExcluded(isExclude).
                setLogMode(logMode);
    }

    private LogLevel getLogLevel() {
        LogLevel logLevel = DEFAULT_LOG_LEVEL;
        try {
            logLevel = conditionFacade.getLogLevel();
        } catch (NjamsInstructionException e) {
            //Do nothing, just use the default
        }
        return logLevel;
    }

    private boolean isExclude() {
        boolean isExclude = DEFAULT_IS_EXCLUDED;
        try {
            isExclude = conditionFacade.isExcluded();
        } catch (NjamsInstructionException e) {
            //Do nothing, just use the default
        }
        return isExclude;
    }

    private LogMode getLogMode() {
        return conditionFacade.getLogMode();
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get LogLevel from {}.", requestReader.getProcessPath());
        }
    }
}