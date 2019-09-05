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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionConstants;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.AbstractProcessorTemplate;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader.EMPTY_STRING;

/**
 * This abstract class provides a template for processing {@link ConditionInstruction conditionInstructions}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class ConditionProcessorTemplate extends AbstractProcessorTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(ConditionProcessorTemplate.class);

    /**
     * Can be used as return value for {@link ConditionProcessorTemplate#getEssentialParametersForProcessing()} if there
     * are no essential parameters.
     */
    protected static final String[] NO_ESSENTIAL_PARAMETERS = new String[0];

    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    protected final ConditionProxy conditionProxy;

    /**
     * Initializes a {@link ConditionProxy conditionProxy} from the given {@link Njams Condition}.
     *
     * @param condition the Condition to read the client's condition from and for example set new {@link Tracepoint
     *                  tracepoints} to.
     */
    public ConditionProcessorTemplate(Njams condition) {
        this.conditionProxy = new ConditionProxy(condition);
    }

    /**
     * Gets or sets data such as {@link Tracepoint tracepoints}, {@link Extract extracts} and so on from/to the
     * {@link ConditionProxy conditionProxy} and writes the response and logs accordingly.
     */
    @Override
    public void process() {

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

    private List<String> fillMissingParametersList() {
        String[] neededParametersForProcessing = getEssentialParametersForProcessing();
        return getRequestReader().collectAllMissingParameters(neededParametersForProcessing);
    }

    /**
     * Returns the parameters that are needed by the {@link ConditionProcessorTemplate instructionProcessor} and must
     * be set in the {@link ConditionRequestReader requestReader's} parameters. The common parameters can be found in
     * the {@link ConditionConstants conditionConstants} interface.
     *
     * @return all Parameters that must have been set in the {@link Request request's} parameters. If no parameters
     * are needed for processing, return {@link ConditionProcessorTemplate#NO_ESSENTIAL_PARAMETERS
     * NO_ESSENTIAL_PARAMETERS} or null.
     */
    protected abstract String[] getEssentialParametersForProcessing();

    private boolean wereAllNeededRequestParametersSet(List<String> missingParameters) {
        return missingParameters.isEmpty();
    }

    private void resetConditionFacade() {
        ConditionRequestReader conditionRequestReader = getRequestReader();
        conditionProxy.setProcessPath(conditionRequestReader.getProcessPath());
        conditionProxy.setActivityId(conditionRequestReader.getActivityId());
    }

    private void setDefaultSuccessResponse() {
        getResponseWriter().setResultCodeAndResultMessage(ResultCode.SUCCESS, DEFAULT_SUCCESS_MESSAGE);
    }

    /**
     * Process the {@link ConditionInstruction instruction}.
     *
     * @throws NjamsInstructionException might be thrown from {@link ConditionProcessorTemplate#saveCondition()} or
     *                                   for example because a {@link ActivityConfiguration activityCondition}
     *                                   couldn't be found.
     */
    protected abstract void processConditionInstruction() throws NjamsInstructionException;

    /**
     * Log the successfully processed {@link ConditionInstruction instruction}.
     */
    protected abstract void logProcessingSuccess();

    private String getProcessingDidntWorkMessage(NjamsInstructionException ex) {
        return ex.getMessage().concat(extractCauseExceptionMessage(ex.getCause()));
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

    private void setWarningResponse(String warningMessage) {
        getResponseWriter().setResultCodeAndResultMessage(ResultCode.WARNING, warningMessage);
    }

    private void logProcessingThrewException(NjamsInstructionException ex) {
        ConditionRequestReader conditionRequestReader = getRequestReader();
        ConditionInstructionExceptionLogger logger = new ConditionInstructionExceptionLogger(
                conditionRequestReader.getCommand(), conditionRequestReader.getProcessPath(),
                conditionRequestReader.getActivityId(), ex);
        logger.log();
    }

    private String GetInvalidParametersMessage(List<String> notEmptyMissingParameters) {
        String invalidParameterMessage = new MissingParameterMessageBuilder(notEmptyMissingParameters).build();

        return invalidParameterMessage;
    }

    private void logInvalidParameters(String invalidParametersMessage) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(invalidParametersMessage);
        }
    }

    /**
     * Returns the {@link ConditionInstruction conditionInstruction} to process by this
     * {@link ConditionProcessorTemplate instructionProcessor}.
     *
     * @return the instruction to process.
     */
    @Override
    public ConditionInstruction getInstruction() {
        return (ConditionInstruction) super.getInstruction();
    }

    /**
     * Returns the instructions {@link ConditionRequestReader conditionRequestReader}.
     *
     * @return the requestReader to the corresponding instruction.
     */
    @Override
    public ConditionRequestReader getRequestReader() {
        return getInstruction().getRequestReader();
    }

    /**
     * Returns the instructions {@link ConditionResponseWriter conditionResponseWriter}.
     *
     * @return the responseWriter to the corresponding instruction.
     */
    @Override
    public ConditionResponseWriter getResponseWriter() {
        return getInstruction().getResponseWriter();
    }

    /**
     * Saves the conditions that are set in the {@link ConditionProxy conditionProxy}.
     *
     * @throws NjamsInstructionException might be thrown if an exception occurs while saving the client condition.
     */
    protected void saveCondition() throws NjamsInstructionException {
        conditionProxy.saveCondition();
    }
}
