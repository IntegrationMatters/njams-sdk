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
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.utils.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConditionInstruction extends AbstractInstruction<ConditionInstruction.ConditionRequestReader, ConditionInstruction.ConditionResponseWriter>{

    public ConditionInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    @Override
    protected ConditionRequestReader createRequestReaderInstance() {
        return new ConditionRequestReader(messageFormatInstruction.getRequest());
    }

    @Override
    protected ConditionResponseWriter createResponseWriterInstance() {
        return new ConditionResponseWriter(messageFormatInstruction.getResponse());
    }

    public static class ConditionRequestReader extends DefaultInstruction.DefaultRequestReader {

        public static final String UNABLE_TO_DESERIALZE_OBJECT = "Unable to deserialize: ";

        private ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

        public ConditionRequestReader(Request requestToRead) {
            super(requestToRead);
        }

        public String getProcessPath() {
            return getParamByConstant(ConditionParameter.PROCESS_PATH);
        }

        private String getParamByConstant(ConditionParameter param) {
            return getParameter(param.getParamKey());
        }

        public String getActivityId() {
            return getParamByConstant(ConditionParameter.ACTIVITY_ID);
        }

        public Extract getExtract() throws NjamsInstructionException {
            return parseJson(getParamByConstant(ConditionParameter.EXTRACT), Extract.class);
        }

        public String getEngineWideRecording() {
            return getParamByConstant(ConditionParameter.ENGINE_WIDE_RECORDING);
        }

        public String getProcessRecording() {
            return getParamByConstant(ConditionParameter.PROCESS_RECORDING);
        }

        public LogLevel getLogLevel() throws NjamsInstructionException {
            return parseEnumParameter(getParamByConstant(ConditionParameter.LOG_LEVEL), LogLevel.class);
        }

        public LogMode getLogMode() throws NjamsInstructionException {
            return parseEnumParameter(getParamByConstant(ConditionParameter.LOG_MODE), LogMode.class);
        }

        public boolean getExcluded() {
            return Boolean.parseBoolean(getParamByConstant(ConditionParameter.EXCLUDE));
        }

        public LocalDateTime getEndTime() throws NjamsInstructionException {
            return parseDateTime(getParamByConstant(ConditionParameter.END_TIME));
        }

        public boolean getTracingEnabled() {
            return Boolean.parseBoolean(getParamByConstant(ConditionParameter.ENABLE_TRACING));
        }

        public LocalDateTime getStartTime() throws NjamsInstructionException {
            return parseDateTime(getParamByConstant(ConditionParameter.START_TIME));
        }

        public Integer getIterations() throws NjamsInstructionException {
            return parseInteger(getParamByConstant(ConditionParameter.ITERATIONS));
        }

        public Boolean getDeepTrace() {
            return Boolean.parseBoolean(getParamByConstant(ConditionParameter.DEEP_TRACE));
        }

        public List<String> searchForMissingParameters(ConditionParameter[] parametersToSearchFor) {
            return Arrays.stream(parametersToSearchFor)
                    .filter(neededParameter -> StringUtils.isBlank(getParamByConstant(neededParameter)))
                    .map(notFoundConditionParameter -> notFoundConditionParameter.getParamKey())
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

        public ConditionResponseWriter(Response response) {
            super(response);
        }

        public ConditionResponseWriter setExtract(Extract extract) throws NjamsInstructionException {
            return putParameter(ConditionParameter.EXTRACT.getParamKey(), serialize(extract));
        }

        private String serialize(Object object) throws NjamsInstructionException {
            try{
                return mapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new NjamsInstructionException("Unable to serialize Object", e);
            }
        }

        public ConditionResponseWriter setLogMode(LogMode logMode){
            return putParameter(ConditionParameter.LOG_MODE.getParamKey(), String.valueOf(logMode));
        }

        public ConditionResponseWriter setLogLevel(LogLevel logLevel){
            return putParameter(ConditionParameter.LOG_LEVEL.getParamKey(), logLevel.name());
        }

        public ConditionResponseWriter setExcluded(boolean isExcluded){
            return putParameter(ConditionParameter.EXCLUDE.getParamKey(), String.valueOf(isExcluded));
        }

        public ConditionResponseWriter setStartTime(LocalDateTime startTime){
            return putParameter(ConditionParameter.START_TIME.getParamKey(), DateTimeUtility.toString(startTime));
        }

        public ConditionResponseWriter setEndTime(LocalDateTime endTime){
            return putParameter(ConditionParameter.END_TIME.getParamKey(), DateTimeUtility.toString(endTime));
        }

        public ConditionResponseWriter setIterations(int iterations){
            return putParameter(ConditionParameter.ITERATIONS.getParamKey(), String.valueOf(iterations));
        }

        public ConditionResponseWriter setDeepTrace(Boolean deepTrace){
            return putParameter(ConditionParameter.DEEP_TRACE.getParamKey(), String.valueOf(deepTrace));
        }
    }
}
