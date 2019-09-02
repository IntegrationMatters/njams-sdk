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

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;

import java.time.LocalDateTime;

/**
 * This class provides methods to write the outgoing instruction's response.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class ConditionResponseWriter extends NjamsResponseWriter<ConditionResponseWriter> {

    private ResponseSerializer responseSerializer = new ResponseSerializer();

    /**
     * Sets the underlying response
     *
     * @param responseToWriteTo the response to set
     */
    public ConditionResponseWriter(Response responseToWriteTo) {
        super(responseToWriteTo);
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#EXTRACT_KEY} to the
     * {@link Response response}.
     *
     * @param extract this parameter will be serialized and set or "null" will be set, if parameter is null
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     * @throws NjamsInstructionException the value of the parameter {@value ConditionConstants#EXTRACT_KEY} cant be
     * serialized.
     */
    public ConditionResponseWriter setExtract(Extract extract) throws NjamsInstructionException {
        return putParameter(ConditionConstants.EXTRACT_KEY, responseSerializer.serializeObject(extract));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#LOG_MODE_KEY} to the
     * {@link Response response}.
     *
     * @param logMode this parameter will be serialized and set or null will be set, if parameter is null
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setLogMode(LogMode logMode) {
        return putParameter(ConditionConstants.LOG_MODE_KEY, responseSerializer.serializeEnum(logMode));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#LOG_LEVEL_KEY} to the
     * {@link Response response}.
     *
     * @param logLevel this parameter will be serialized and set or null will be set, if parameter is null.
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setLogLevel(LogLevel logLevel) {
        return putParameter(ConditionConstants.LOG_LEVEL_KEY, responseSerializer.serializeEnum(logLevel));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#EXCLUDE_KEY} to the
     * {@link Response response}.
     *
     * @param isExcluded this parameter will be serialized and set
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setExcluded(boolean isExcluded) {
        return putParameter(ConditionConstants.EXCLUDE_KEY, responseSerializer.serializeBoolean(isExcluded));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#START_TIME_KEY} to the
     * {@link Response response}.
     *
     * @param startTime this parameter will be serialized and set or null will be set, if parameter is null.
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setStartTime(LocalDateTime startTime) {
        return putParameter(ConditionConstants.START_TIME_KEY, responseSerializer.serializeDateTime(startTime));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#END_TIME_KEY} to the
     * {@link Response response}.
     *
     * @param endTime this parameter will be serialized and set or null will be set, if parameter is null.
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setEndTime(LocalDateTime endTime) {
        return putParameter(ConditionConstants.END_TIME_KEY, responseSerializer.serializeDateTime(endTime));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#ITERATIONS_KEY} to the
     * {@link Response response}.
     *
     * @param iterations this parameter will be serialized and set.
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setIterations(int iterations) {
        return putParameter(ConditionConstants.ITERATIONS_KEY, responseSerializer.serializeInteger(iterations));
    }

    /**
     * Sets the parameter value for the key {@value ConditionConstants#DEEP_TRACE_KEY} with the given string to the
     * {@link Response response}.
     *
     * @param deepTrace this parameter will be serialized and set
     * @return itself via {@link #getThis() getThis()} for chaining ConditionResponseWriter methods
     */
    public ConditionResponseWriter setDeepTrace(boolean deepTrace) {
        return putParameter(ConditionConstants.DEEP_TRACE_KEY, responseSerializer.serializeBoolean(deepTrace));
    }

    /**
     * This class encapsulates serializing of the response's parameters from the actual logic of the outer class.
     */
    private static class ResponseSerializer {

        private final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

        private String serializeObject(Object object) throws NjamsInstructionException {
            try {
                return mapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new NjamsInstructionException("Unable to serialize Object", e);
            }
        }

        private String serializeEnum(Enum enumParameter) {
            return enumParameter != null ? enumParameter.name() : null;
        }

        private String serializeBoolean(boolean booleanParameter) {
            return String.valueOf(booleanParameter);
        }

        private String serializeInteger(int integerParameter) {
            return String.valueOf(integerParameter);
        }

        private String serializeDateTime(LocalDateTime localDateTime) {
            return DateTimeUtility.toString(localDateTime);
        }
    }
}
