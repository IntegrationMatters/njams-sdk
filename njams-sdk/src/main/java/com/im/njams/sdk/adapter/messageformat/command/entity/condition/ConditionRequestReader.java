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

/**
 * This class provides methods to read the incoming instruction's request.
 *
 * @author krautenberg
 * @version 4.1.0
 */
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

    /**
     * Returns the parameter value to the key {@value ConditionConstants#PROCESS_PATH_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#PROCESS_PATH_KEY} or null if not found.
     */
    public String getProcessPath() {
        return getParameter(ConditionConstants.PROCESS_PATH_KEY);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#ACTIVITY_ID_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#ACTIVITY_ID_KEY} or null if not found.
     */
    public String getActivityId() {
        return getParameter(ConditionConstants.ACTIVITY_ID_KEY);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#EXTRACT_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#EXTRACT_KEY} or "null" if not found.
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#EXTRACT_KEY} cant be
     * parsed to an {@link Extract extract}.
     */
    public Extract getExtract() throws NjamsInstructionException {
        return requestParser.parseJson(getParameter(ConditionConstants.EXTRACT_KEY), Extract.class);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#ENGINE_WIDE_RECORDING_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#ENGINE_WIDE_RECORDING_KEY} or null if not found.
     */
    public String getEngineWideRecording() {
        return getParameter(ConditionConstants.ENGINE_WIDE_RECORDING_KEY);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#PROCESS_RECORDING_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#PROCESS_RECORDING_KEY} or null if not found.
     */
    public String getProcessRecording() {
        return getParameter(ConditionConstants.PROCESS_RECORDING_KEY);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#LOG_LEVEL_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#LOG_LEVEL_KEY} or null if not found.
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#LOG_LEVEL_KEY} cant be
     *                                   parsed to a {@link LogLevel LogLevel}.
     */
    public LogLevel getLogLevel() throws NjamsInstructionException {
        return requestParser.parseEnumParameter(getParameter(ConditionConstants.LOG_LEVEL_KEY), LogLevel.class);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#LOG_MODE_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#LOG_MODE_KEY} or null if not found.
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#LOG_MODE_KEY} cant be
     *                                   parsed to a
     *                                   {@link LogMode LogMode}.
     */
    public LogMode getLogMode() throws NjamsInstructionException {
        return requestParser.parseEnumParameter(getParameter(ConditionConstants.LOG_MODE_KEY), LogMode.class);
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#EXCLUDE_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#EXCLUDE_KEY} or false if not found.
     */
    public boolean getExcluded() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.EXCLUDE_KEY));
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#END_TIME_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#END_TIME_KEY} or null if not found.
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#END_TIME_KEY} cant be
     *                                   parsed to a {@link LocalDateTime LocalDateTime}.
     */
    public LocalDateTime getEndTime() throws NjamsInstructionException {
        return requestParser.parseDateTime(getParameter(ConditionConstants.END_TIME_KEY));
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#ENABLE_TRACING_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#ENABLE_TRACING_KEY} or false if not found.
     */
    public boolean getTracingEnabled() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.ENABLE_TRACING_KEY));
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#START_TIME_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#START_TIME_KEY} or null if not found.
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#START_TIME_KEY} cant be
     *                                   parsed to a {@link LocalDateTime LocalDateTime}.
     */
    public LocalDateTime getStartTime() throws NjamsInstructionException {
        return requestParser.parseDateTime(getParameter(ConditionConstants.START_TIME_KEY));
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#ITERATIONS_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#ITERATIONS_KEY}
     * @throws NjamsInstructionException the value to {@value ConditionConstants#ITERATIONS_KEY} is not an integer or
     *                                   null.
     */
    public Integer getIterations() throws NjamsInstructionException {
        return requestParser.parseInteger(getParameter(ConditionConstants.ITERATIONS_KEY));
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#DEEP_TRACE_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#DEEP_TRACE_KEY} or false if not found.
     */
    public Boolean getDeepTrace() {
        return requestParser.parseBoolean(getParameter(ConditionConstants.DEEP_TRACE_KEY));
    }

    /**
     * Returns a list of all parameters that are in parametersToSearchFor, but not the underlying request's parameters.
     *
     * @param parametersToSearchFor the parameters to search for in the request's parameters.
     * @return a {@link List<String> List} of parameters that are missing in the request's parameters or an
     * {@link Collections#EMPTY_LIST empty list}, if all parameters were found.
     */
    public List<String> collectAllMissingParameters(String[] parametersToSearchFor) {
        if (parametersToSearchFor != null) {
            return Arrays.stream(parametersToSearchFor).filter(neededParameter -> isParameterMissing(neededParameter))
                    .collect(Collectors.toList());
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Returns the parameter value to the key {@value ConditionConstants#ACTIVITY_ID_KEY}.
     *
     * @return the value of the parameter {@value ConditionConstants#ACTIVITY_ID_KEY} or null if not found.
     */
    private boolean isParameterMissing(String parameterToCheck) {
        return StringUtils.isBlank(getParameter(parameterToCheck));
    }

    /**
     * This class encapsulates parsing of the request's parameters from the actual logic of the outer class.
     */
    private static class RequestParser {

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
