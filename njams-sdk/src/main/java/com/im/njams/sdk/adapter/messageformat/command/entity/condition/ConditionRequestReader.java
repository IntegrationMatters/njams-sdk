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

package com.im.njams.sdk.adapter.messageformat.command.entity.condition;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.utils.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConditionRequestReader extends NjamsRequestReader {

    private RequestParser requestParser = new RequestParser();

    /**
     * Sets the underlying request
     *
     * @param requestToReadFrom the request to set
     */
    public ConditionRequestReader(Request requestToReadFrom) {
        super(requestToReadFrom);
    }

    public String getProcessPath() {
        return getParameter(ConditionConstants.PROCESS_PATH_KEY);
    }

    public String getActivityId() {
        return getParameter(ConditionConstants.ACTIVITY_ID_KEY);
    }

    public Extract getExtract() throws NjamsInstructionException {
        return requestParser.parseJson(getParameter(ConditionConstants.EXTRACT_KEY), Extract.class);
    }

    public String getEngineWideRecording() {
        return getParameter(ConditionConstants.ENGINE_WIDE_RECORDING_KEY);
    }

    public String getProcessRecording() {
        return getParameter(ConditionConstants.PROCESS_RECORDING_KEY);
    }

    public LogLevel getLogLevel() throws NjamsInstructionException {
        return requestParser.parseEnumParameter(getParameter(ConditionConstants.LOG_LEVEL_KEY), LogLevel.class);
    }

    public LogMode getLogMode() throws NjamsInstructionException {
        return requestParser.parseEnumParameter(getParameter(ConditionConstants.LOG_MODE_KEY), LogMode.class);
    }

    public boolean getExcluded() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.EXCLUDE_KEY));
    }

    public LocalDateTime getEndTime() throws NjamsInstructionException {
        return requestParser.parseDateTime(getParameter(ConditionConstants.END_TIME_KEY));
    }

    public boolean getTracingEnabled() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.ENABLE_TRACING_KEY));
    }

    public LocalDateTime getStartTime() throws NjamsInstructionException {
        return requestParser.parseDateTime(getParameter(ConditionConstants.START_TIME_KEY));
    }

    public Integer getIterations() throws NjamsInstructionException {
        return requestParser.parseInteger(getParameter(ConditionConstants.ITERATIONS_KEY));
    }

    public Boolean getDeepTrace() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.DEEP_TRACE_KEY));
    }

    public List<String> collectAllMissingParameters(String[] parametersToSearchFor) {
        if (parametersToSearchFor != null) {
            return Arrays.stream(parametersToSearchFor).filter(neededParameter -> isParameterMissing(neededParameter))
                    .collect(Collectors.toList());
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    private boolean isParameterMissing(String parameterToCheck) {
        return StringUtils.isBlank(getParameter(parameterToCheck));
    }

    private static class RequestParser{

        private ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

        private <T extends Enum<T>> T parseEnumParameter(final String enumParameterToParse, final Class<T> enumeration)
                throws NjamsInstructionException {
            T foundEnumParameter = null;
            if (enumParameterToParse != null && enumeration != null && enumeration.getEnumConstants() != null) {
                foundEnumParameter = Arrays.stream(enumeration.getEnumConstants())
                        .filter(c -> c.name().equalsIgnoreCase(enumParameterToParse)).findAny().orElse(null);
            }
            if (foundEnumParameter == null) {
                throw new NjamsInstructionException(
                        NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT + "\"" + enumParameterToParse + "\"" + " to " +
                        enumeration.getSimpleName());
            }
            return foundEnumParameter;
        }

        private <T> T parseJson(String objectToParse, Class<T> type) throws NjamsInstructionException {
            try {
                return mapper.readValue(objectToParse, type);
            } catch (IOException parsingException) {
                throw new NjamsInstructionException(
                        NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT + "\"" + objectToParse + "\"" + " to " +
                        type.getSimpleName(), parsingException);
            }
        }

        private LocalDateTime parseDateTime(String localDateTimeString) throws NjamsInstructionException {
            if (StringUtils.isBlank(localDateTimeString)) {
                return null;
            }
            try {
                return DateTimeUtility.fromString(localDateTimeString);
            } catch (RuntimeException parsingException) {
                throw new NjamsInstructionException(NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT + localDateTimeString,
                        parsingException);
            }
        }

        private Integer parseInteger(String integerAsString) throws NjamsInstructionException {
            try {
                return Integer.parseInt(integerAsString);
            } catch (NumberFormatException invalidIntegerException) {
                throw new NjamsInstructionException(NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT + integerAsString,
                        invalidIntegerException);
            }
        }

        private Boolean parseBoolean(String parameter) {
            return Boolean.parseBoolean(parameter);
        }
    }
}
