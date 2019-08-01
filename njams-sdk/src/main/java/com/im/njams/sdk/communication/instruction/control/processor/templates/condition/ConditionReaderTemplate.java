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

package com.im.njams.sdk.communication.instruction.control.processor.templates.condition;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.communication.instruction.control.processor.templates.InstructionProcessorTemplate;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction.DefaultRequestReader.EMPTY_STRING;

public abstract class ConditionReaderTemplate extends InstructionProcessorTemplate<ConditionInstruction> {

    private static final Logger LOG = LoggerFactory.getLogger(ConditionReaderTemplate.class);

    protected static final ConditionParameter[] NO_ESSENTIAL_PARAMETERS = new ConditionParameter[0];

    static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    protected final ConditionProxy conditionFacade;

    protected ConditionInstruction.ConditionRequestReader requestReader;

    protected ConditionInstruction.ConditionResponseWriter responseWriter;

    public ConditionReaderTemplate(Njams njams) {
        this.conditionFacade = new ConditionProxy(njams);
    }

    @Override
    public void process() {

        setReaderAndWriter();

        List<String> missingParameters = fillMissingParametersList();

        if (wereAllNeededRequestParametersSet(missingParameters)) {
            resetConditionFacade();
            setDefaultSuccessResponse();
            try {
                processConditionInstruction();
                logProcessingSuccess();
            } catch (final NjamsInstructionException ex) {
                String processingExceptionMessage = getProcessingDidntWorkMessage(ex);
                setWarningResponse(processingExceptionMessage);
                logProcessingThrewException(ex);
            }
        } else {
            String invalidParametersMessage = GetInvalidParametersMessage(missingParameters);
            setWarningResponse(invalidParametersMessage);
            logInvalidParameters(invalidParametersMessage);
        }
    }

    void setReaderAndWriter() {
        requestReader = getInstruction().getRequestReader();
        responseWriter = getInstruction().getResponseWriter();
    }

    List<String> fillMissingParametersList() {
        ConditionParameter[] neededParametersForProcessing = getEssentialParametersForProcessing();
        return requestReader.searchForMissingParameters(neededParametersForProcessing);
    }

    protected abstract ConditionParameter[] getEssentialParametersForProcessing();

    boolean wereAllNeededRequestParametersSet(List<String> missingParameters) {
        return missingParameters.isEmpty();
    }

    void resetConditionFacade() {
        conditionFacade.setProcessPath(requestReader.getProcessPath());
        conditionFacade.setActivityId(requestReader.getActivityId());
    }

    void setDefaultSuccessResponse() {
        responseWriter.setResultCodeAndResultMessage(ResponseWriter.ResultCode.SUCCESS, DEFAULT_SUCCESS_MESSAGE);
    }

    protected abstract void processConditionInstruction() throws NjamsInstructionException;

    protected abstract void logProcessingSuccess();

    String getProcessingDidntWorkMessage(NjamsInstructionException ex) {
        return ex.getMessage().concat(extractCauseExceptionMessage(ex.getCause()));
    }

    void setWarningResponse(String warningMessage) {
        responseWriter.setResultCodeAndResultMessage(ResponseWriter.ResultCode.WARNING, warningMessage);
    }

    private String extractCauseExceptionMessage(Throwable sourceException) {
        String resultMessageToReturn = EMPTY_STRING;
        if (sourceException != null) {
            String extractedMessage = sourceException.getMessage();
            if (StringUtils.isNotBlank(extractedMessage)) {
                resultMessageToReturn = ": " + extractedMessage;
            }
        }
        return resultMessageToReturn;
    }

    void logProcessingThrewException(NjamsInstructionException ex) {
        ConditionInstructionExceptionLogger logger = new ConditionInstructionExceptionLogger(requestReader.getCommand(),
                requestReader.getProcessPath(), requestReader.getActivityId(), ex);
        logger.log();
    }

    String GetInvalidParametersMessage(List<String> notEmptyMissingParameters) {
        String invalidParameterMessage = new MissingParameterMessageBuilder(notEmptyMissingParameters).build();

        return invalidParameterMessage;
    }

    void logInvalidParameters(String invalidParametersMessage) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(invalidParametersMessage);
        }
    }
}
