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

package com.im.njams.sdk.communication.receiver.instruction.control.templates.condition;

import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.im.njams.sdk.utils.StringUtils.isBlank;

class ConditionInstructionExceptionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ConditionInstructionExceptionLogger.class);

    static final String INVALID_INSTRUCTION = "Instruction is invalid";

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
            logString(INVALID_INSTRUCTION);
        } else {
            String loggableMessage = createLoggableMessage();
            if (throwableToLog == null) {
                logString(loggableMessage);
            } else {
                logStringAndException(loggableMessage, throwableToLog);
            }
        }
    }

    void logString(String toLog) {
        if (LOG.isErrorEnabled()) {
            LOG.error(toLog);
        }
    }

    void logStringAndException(String stringToLog, Throwable throwableToLog) {
        if (LOG.isErrorEnabled()) {
            LOG.error(stringToLog, throwableToLog);
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
        }
        builder.append(".");
        if (isNotBlank(exceptionMessageToLog)) {
            builder.append(" Reason: ").append(exceptionMessageToLog);
        }
        return builder.toString();
    }


    private boolean isNotBlank(String paramToCheckIfNullOrEmpty) {
        return StringUtils.isNotBlank(paramToCheckIfNullOrEmpty);
    }
}
