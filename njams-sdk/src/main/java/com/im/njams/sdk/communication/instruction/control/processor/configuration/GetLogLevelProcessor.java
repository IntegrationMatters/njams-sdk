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
import com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionFacade;
import com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionReaderTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class GetLogLevelProcessor extends ConditionReaderTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogLevelProcessor.class);

    private static final ConditionParameter[] neededParameter =
            new ConditionParameter[]{ConditionParameter.PROCESS_PATH};


    public GetLogLevelProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected ConditionParameter[] getNeededParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void processConditionInstruction() {
        ConditionFacade clientCondition = getClientCondition();

        final LogLevel logLevel = getLogLevel(clientCondition);
        final boolean isExclude = isExclude(clientCondition);
        final LogMode logMode = clientCondition.getLogMode();

        getConditionResponseWriter().setLogLevel(logLevel).setExcluded(isExclude).setLogMode(logMode);
    }

    private LogLevel getLogLevel(ConditionFacade clientCondition) {
        //default
        LogLevel logLevel = LogLevel.INFO;
        try {
            logLevel = clientCondition.getLogLevel();
        } catch (NjamsInstructionException e) {
            //Do nothing, just use the default
        }
        return logLevel;
    }

    private boolean isExclude(ConditionFacade clientCondition) {
        //default
        boolean isExclude = false;
        try{
            isExclude = clientCondition.isExcluded();
        } catch (NjamsInstructionException e) {
            //Do nothing, just use the default
        }
        return isExclude;
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            ConditionRequestReader requestReader = getConditionRequestReader();
            LOG.debug("Get LogLevel from {}.", requestReader.getProcessPath());
        }
    }
}
