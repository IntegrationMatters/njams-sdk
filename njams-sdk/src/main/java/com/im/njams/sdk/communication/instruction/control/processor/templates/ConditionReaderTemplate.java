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
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.im.njams.sdk.adapter.messageformat.command.entity.DefaultRequestReader.EMPTY_STRING;
import static com.im.njams.sdk.utils.StringUtils.isBlank;

public abstract class ConditionReaderTemplate extends InstructionProcessorTemplate<ConditionInstruction> {

    static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    private Njams njams;

    private List<String> missingParameters;

    private ConditionRequestReader reader;

    private ConditionResponseWriter writer;

    public ConditionReaderTemplate(Njams njams) {
        this.njams = njams;
    }

    @Override
    public void process() {
        clear();

        setDefaultSuccessResponse();

        if (wereNeededRequestParametersSet()) {
            try {
                processConditionInstruction();
                logProcessingSuccess();
            } catch (final NjamsInstructionException ex) {
                setProcessingDidntWorkResponse(ex);
                logProcessingThrewException(ex);
            }
        } else {
            setInvalidParametersResponse();
        }
    }

    private void clear() {
        missingParameters = new ArrayList<>();
        reader = getInstruction().getRequestReader();
        writer = getInstruction().getResponseWriter();
    }

    void setDefaultSuccessResponse() {
        if (writer.isEmpty()) {
            writer.setResultCodeAndResultMessage(ResponseWriter.ResultCode.SUCCESS, DEFAULT_SUCCESS_MESSAGE);
        }
    }

    boolean wereNeededRequestParametersSet() {
        ConditionParameter[] neededParametersForProcessing = getNeededParametersForProcessing();
        missingParameters = reader.searchForMissingParameters(neededParametersForProcessing);
        return missingParameters.isEmpty();
    }

    protected abstract ConditionParameter[] getNeededParametersForProcessing();

    protected abstract void processConditionInstruction() throws NjamsInstructionException;

    protected abstract void logProcessingSuccess();

    void setProcessingDidntWorkResponse(NjamsInstructionException ex) {
        final Throwable sourceException = ex.getCause();
        String sourceExceptionMessage = EMPTY_STRING;
        if (sourceException != null) {
            sourceExceptionMessage = sourceException.getMessage();
        }
        setWarningResponse(ex.getMessage() + ": " + sourceExceptionMessage);
    }

    private void setWarningResponse(String warningMessage) {
        writer.setResultCodeAndResultMessage(ResponseWriter.ResultCode.WARNING, warningMessage);
    }

    void logProcessingThrewException(NjamsInstructionException ex) {
        ConditionInstructionExceptionLogger logger = new ConditionInstructionExceptionLogger(reader.getCommand(),
                reader.getProcessPath(), reader.getActivityId(), ex);
        logger.log();
    }

    void setInvalidParametersResponse() {
        setWarningResponse("missing parameter(s): " + missingParameters);
    }

    public Njams getClientCondition() {
        return njams;
    }

    public ConditionRequestReader getConditionRequestReader() {
        return getInstruction().getRequestReader();
    }

    public ConditionResponseWriter getConditionResponseWriter() {
        return getInstruction().getResponseWriter();
    }

    private static class ConditionInstructionExceptionLogger {

        private static final Logger LOG = LoggerFactory.getLogger(ConditionInstructionExceptionLogger.class);

        private String commandToLog;
        private String processPathToLog;
        private String activityIdToLog;
        private String exceptionMessageToLog;
        private Throwable throwableToLog;

        public ConditionInstructionExceptionLogger(String command, String processPath, String activityId,
                NjamsInstructionException ex) {
            commandToLog = command;
            processPathToLog = processPath;
            activityIdToLog = activityId;
            if (ex != null) {
                exceptionMessageToLog = ex.getMessage();
                throwableToLog = ex.getCause();
            }
        }

        public void log() {
            if (isBlank(commandToLog)) {
                LOG.error("Instruction is invalid");
            } else {
                String loggableMessage = createLoggableMessage();
                if (throwableToLog == null) {
                    LOG.error(loggableMessage);
                } else {
                    LOG.error(loggableMessage, throwableToLog);
                }
            }
        }

        private String createLoggableMessage() {
            StringBuilder builder = new StringBuilder();
            builder.append("Failed to execute command: [").append(commandToLog).append("]");
            if (isNotBlank(processPathToLog)) {
                builder.append(" on process: ").append(processPathToLog);
                if (isNotBlank(activityIdToLog)) {
                    builder.append("#").append(activityIdToLog);
                }
                builder.append(".");
            }
            if (isNotBlank(exceptionMessageToLog)) {
                builder.append(" Reason: ").append(exceptionMessageToLog);
            }
            return builder.toString();
        }


        private boolean isNotBlank(String paramToCheckIfNullOrEmpty) {
            return StringUtils.isNotBlank(paramToCheckIfNullOrEmpty);
        }
    }
}
