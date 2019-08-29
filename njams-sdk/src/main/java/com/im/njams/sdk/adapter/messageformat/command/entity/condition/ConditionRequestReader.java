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
import com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultRequestReader;
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

public class ConditionRequestReader extends DefaultRequestReader {

    private ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    protected ConditionRequestReader(Request requestToRead) {
        super(requestToRead);
    }

    public String getProcessPath() {
        return getParameter(ConditionInstruction.PROCESS_PATH);
    }

    public String getActivityId() {
        return getParameter(ConditionInstruction.ACTIVITY_ID);
    }

    public Extract getExtract() throws NjamsInstructionException {
        return parseJson(getParameter(ConditionInstruction.EXTRACT), Extract.class);
    }

    public String getEngineWideRecording() {
        return getParameter(ConditionInstruction.ENGINE_WIDE_RECORDING);
    }

    public String getProcessRecording() {
        return getParameter(ConditionInstruction.PROCESS_RECORDING);
    }

    public LogLevel getLogLevel() throws NjamsInstructionException {
        return parseEnumParameter(getParameter(ConditionInstruction.LOG_LEVEL), LogLevel.class);
    }

    public LogMode getLogMode() throws NjamsInstructionException {
        return parseEnumParameter(getParameter(ConditionInstruction.LOG_MODE), LogMode.class);
    }

    public boolean getExcluded() {
        return Boolean.parseBoolean(getParameter(ConditionInstruction.EXCLUDE));
    }

    public LocalDateTime getEndTime() throws NjamsInstructionException {
        return parseDateTime(getParameter(ConditionInstruction.END_TIME));
    }

    public boolean getTracingEnabled() {
        return Boolean.parseBoolean(getParameter(ConditionInstruction.ENABLE_TRACING));
    }

    public LocalDateTime getStartTime() throws NjamsInstructionException {
        return parseDateTime(getParameter(ConditionInstruction.START_TIME));
    }

    public Integer getIterations() throws NjamsInstructionException {
        return parseInteger(getParameter(ConditionInstruction.ITERATIONS));
    }

    public Boolean getDeepTrace() {
        return Boolean.parseBoolean(getParameter(ConditionInstruction.DEEP_TRACE));
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

    private <T extends Enum<T>> T parseEnumParameter(final String enumParameterToParse, final Class<T> enumeration)
            throws NjamsInstructionException {
        T foundEnumParameter = null;
        if (enumParameterToParse != null && enumeration != null && enumeration.getEnumConstants() != null) {
            foundEnumParameter = Arrays.stream(enumeration.getEnumConstants())
                    .filter(c -> c.name().equalsIgnoreCase(enumParameterToParse)).findAny().orElse(null);
        }
        if (foundEnumParameter == null) {
            throw new NjamsInstructionException(
                    AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT + "\"" + enumParameterToParse + "\"" + " to " +
                    enumeration.getSimpleName());
        }
        return foundEnumParameter;
    }

    private <T> T parseJson(String objectToParse, Class<T> type) throws NjamsInstructionException {
        try {
            return mapper.readValue(objectToParse, type);
        } catch (IOException parsingException) {
            throw new NjamsInstructionException(
                    AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT + "\"" + objectToParse + "\"" + " to " +
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
            throw new NjamsInstructionException(AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT + localDateTimeString,
                    parsingException);
        }
    }

    private Integer parseInteger(String integerAsString) throws NjamsInstructionException {
        try {
            return Integer.parseInt(integerAsString);
        } catch (NumberFormatException invalidIntegerException) {
            throw new NjamsInstructionException(AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT + integerAsString,
                    invalidIntegerException);
        }
    }
}
