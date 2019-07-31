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

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.JsonSerializerFactory;

import java.time.LocalDateTime;

public class ConditionResponseWriter extends DefaultResponseWriter<ConditionResponseWriter> {

    private final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();

    public ConditionResponseWriter(Response response) {
        super(response);
    }

    public boolean isEmpty(){
        return responseToBuild == null;
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
