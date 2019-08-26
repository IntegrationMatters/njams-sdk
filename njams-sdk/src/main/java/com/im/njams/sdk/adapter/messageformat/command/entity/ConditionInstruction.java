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

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.utils.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction.UNABLE_TO_DESERIALZE_OBJECT;

public class ConditionInstruction extends AbstractInstruction<ConditionInstruction.ConditionRequestReader, ConditionInstruction.ConditionResponseWriter>{

    public static final String PROCESS_PATH = "processPath";
    public static final String ACTIVITY_ID = "activityId";
    public static final String EXTRACT = "extract";
    public static final String LOG_LEVEL = "logLevel";
    public static final String LOG_MODE = "logMode";
    public static final String EXCLUDE = "exclude";
    public static final String START_TIME = "starttime";
    public static final String END_TIME = "endtime";
    public static final String ITERATIONS = "iterations";
    public static final String DEEP_TRACE = "deepTrace";
    public static final String ENGINE_WIDE_RECORDING = "EngineWideRecording";
    public static final String PROCESS_RECORDING = "Record";
    public static final String ENABLE_TRACING = "enableTracing";

    public ConditionInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    @Override
    protected ConditionRequestReader createRequestReaderInstance(Request request) {
        return new ConditionRequestReader(request);
    }

    @Override
    protected ConditionResponseWriter createResponseWriterInstance(Response response) {
        return new ConditionResponseWriter(response);
    }

    public static class ConditionRequestReader extends DefaultInstruction.DefaultRequestReader {

        private ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

        protected ConditionRequestReader(Request requestToRead) {
            super(requestToRead);
        }

        public String getProcessPath() {
            return getParameter(PROCESS_PATH);
        }

        public String getActivityId() {
            return getParameter(ACTIVITY_ID);
        }

        public Extract getExtract() throws NjamsInstructionException {
            return parseJson(getParameter(EXTRACT), Extract.class);
        }

        public String getEngineWideRecording() {
            return getParameter(ENGINE_WIDE_RECORDING);
        }

        public String getProcessRecording() {
            return getParameter(PROCESS_RECORDING);
        }

        public LogLevel getLogLevel() throws NjamsInstructionException {
            return parseEnumParameter(getParameter(LOG_LEVEL), LogLevel.class);
        }

        public LogMode getLogMode() throws NjamsInstructionException {
            return parseEnumParameter(getParameter(LOG_MODE), LogMode.class);
        }

        public boolean getExcluded() {
            return Boolean.parseBoolean(getParameter(EXCLUDE));
        }

        public LocalDateTime getEndTime() throws NjamsInstructionException {
            return parseDateTime(getParameter(END_TIME));
        }

        public boolean getTracingEnabled() {
            return Boolean.parseBoolean(getParameter(ENABLE_TRACING));
        }

        public LocalDateTime getStartTime() throws NjamsInstructionException {
            return parseDateTime(getParameter(START_TIME));
        }

        public Integer getIterations() throws NjamsInstructionException {
            return parseInteger(getParameter(ITERATIONS));
        }

        public Boolean getDeepTrace() {
            return Boolean.parseBoolean(getParameter(DEEP_TRACE));
        }

        public List<String> searchForMissingParameters(String[] parametersToSearchFor) {
            return Arrays.stream(parametersToSearchFor)
                    .filter(neededParameter -> StringUtils.isBlank(getParameter(neededParameter)))
                    .collect(Collectors.toList());
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
                        UNABLE_TO_DESERIALZE_OBJECT + "\"" + enumParameterToParse + "\"" + " to " +
                        enumeration.getSimpleName());
            }
            return foundEnumParameter;
        }

        private <T> T parseJson(String objectToParse, Class<T> type) throws NjamsInstructionException {
            try {
                return mapper.readValue(objectToParse, type);
            } catch (IOException parsingException) {
                throw new NjamsInstructionException(
                        UNABLE_TO_DESERIALZE_OBJECT + "\"" + objectToParse + "\"" + " to " + type.getSimpleName(),
                        parsingException);
            }
        }

        private LocalDateTime parseDateTime(String localDateTimeString) throws NjamsInstructionException {
            if (StringUtils.isBlank(localDateTimeString)) {
                return null;
            }
            try {
                return DateTimeUtility.fromString(localDateTimeString);
            } catch (RuntimeException parsingException) {
                throw new NjamsInstructionException(UNABLE_TO_DESERIALZE_OBJECT + localDateTimeString, parsingException);
            }
        }

        private Integer parseInteger(String integerAsString) throws NjamsInstructionException {
            try {
                return Integer.parseInt(integerAsString);
            } catch (NumberFormatException invalidIntegerException) {
                throw new NjamsInstructionException(UNABLE_TO_DESERIALZE_OBJECT + integerAsString, invalidIntegerException);
            }
        }
    }

    public static class ConditionResponseWriter extends DefaultInstruction.DefaultResponseWriter<ConditionResponseWriter> {

        private final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

        protected ConditionResponseWriter(Response response) {
            super(response);
        }

        public ConditionResponseWriter setExtract(Extract extract) throws NjamsInstructionException {
            return putParameter(EXTRACT, serialize(extract));
        }

        private String serialize(Object object) throws NjamsInstructionException {
            try{
                return mapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new NjamsInstructionException("Unable to serialize Object", e);
            }
        }

        public ConditionResponseWriter setLogMode(LogMode logMode){
            return putParameter(LOG_MODE, String.valueOf(logMode));
        }

        public ConditionResponseWriter setLogLevel(LogLevel logLevel){
            return putParameter(LOG_LEVEL, logLevel.name());
        }

        public ConditionResponseWriter setExcluded(boolean isExcluded){
            return putParameter(EXCLUDE, String.valueOf(isExcluded));
        }

        public ConditionResponseWriter setStartTime(LocalDateTime startTime){
            return putParameter(START_TIME, DateTimeUtility.toString(startTime));
        }

        public ConditionResponseWriter setEndTime(LocalDateTime endTime){
            return putParameter(END_TIME, DateTimeUtility.toString(endTime));
        }

        public ConditionResponseWriter setIterations(int iterations){
            return putParameter(ITERATIONS, String.valueOf(iterations));
        }

        public ConditionResponseWriter setDeepTrace(Boolean deepTrace){
            return putParameter(DEEP_TRACE, String.valueOf(deepTrace));
        }
    }
}
