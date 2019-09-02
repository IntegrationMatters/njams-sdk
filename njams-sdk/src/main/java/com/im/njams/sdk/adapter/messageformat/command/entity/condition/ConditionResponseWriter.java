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

public class ConditionResponseWriter extends NjamsResponseWriter<ConditionResponseWriter> {

    private final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    /**
     * Sets the underlying response
     *
     * @param responseToWriteTo the response to set
     */
    public ConditionResponseWriter(Response responseToWriteTo) {
        super(responseToWriteTo);
    }

    public ConditionResponseWriter setExtract(Extract extract) throws NjamsInstructionException {
        return putParameter(ConditionConstants.EXTRACT_KEY, serialize(extract));
    }

    private String serialize(Object object) throws NjamsInstructionException {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new NjamsInstructionException("Unable to serialize Object", e);
        }
    }

    public ConditionResponseWriter setLogMode(LogMode logMode) {
        return putParameter(ConditionConstants.LOG_MODE_KEY, logMode != null ? String.valueOf(logMode) : null);
    }

    public ConditionResponseWriter setLogLevel(LogLevel logLevel) {
        return putParameter(ConditionConstants.LOG_LEVEL_KEY, logLevel != null ? logLevel.name() : null);
    }

    public ConditionResponseWriter setExcluded(boolean isExcluded) {
        return putParameter(ConditionConstants.EXCLUDE_KEY, String.valueOf(isExcluded));
    }

    public ConditionResponseWriter setStartTime(LocalDateTime startTime) {
        return putParameter(ConditionConstants.START_TIME_KEY, DateTimeUtility.toString(startTime));
    }

    public ConditionResponseWriter setEndTime(LocalDateTime endTime) {
        return putParameter(ConditionConstants.END_TIME_KEY, DateTimeUtility.toString(endTime));
    }

    public ConditionResponseWriter setIterations(int iterations) {
        return putParameter(ConditionConstants.ITERATIONS_KEY, String.valueOf(iterations));
    }

    public ConditionResponseWriter setDeepTrace(Boolean deepTrace) {
        return putParameter(ConditionConstants.DEEP_TRACE_KEY, String.valueOf(deepTrace));
    }
}
