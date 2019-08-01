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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultInstruction extends AbstractInstruction<DefaultInstruction.DefaultRequestReader, DefaultInstruction.DefaultResponseWriter> {

    public static final String UNABLE_TO_DESERIALZE_OBJECT = "Unable to deserialize: ";

    public DefaultInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    @Override
    protected DefaultRequestReader createRequestReaderInstance() {
        return new DefaultRequestReader(messageFormatInstruction.getRequest());
    }

    @Override
    protected DefaultResponseWriter createResponseWriterInstance() {
        return new DefaultResponseWriter(messageFormatInstruction.getResponse());
    }

    public static class DefaultRequestReader implements AbstractInstruction.RequestReader {

        public static final String EMPTY_STRING = "";

        protected Request requestToRead;

        public DefaultRequestReader(Request requestToRead) {
            this.requestToRead = requestToRead;
        }

        @Override
        public boolean isEmpty(){
            return requestToRead == null;
        }

        @Override
        public boolean isCommandNull(){
            return isEmpty() || requestToRead.getCommand() == null;
        }

        @Override
        public boolean isCommandEmpty(){
            return isCommandNull() || requestToRead.getCommand().equals(EMPTY_STRING);
        }

        @Override
        public String getCommand() {
            String foundCommand = EMPTY_STRING;
            if(!isCommandNull()){
                foundCommand = requestToRead.getCommand();
            }
            return foundCommand;
        }

        @Override
        public Map<String, String> getParameters() {
            if(!isEmpty()){
                return Collections.unmodifiableMap(requestToRead.getParameters());
            }
            return Collections.EMPTY_MAP;
        }

        @Override
        public String getParameter(String paramKey) {
            return getParameters().get(paramKey);
        }

        @Override
        public String toString(){
            try {
                return JsonUtils.serialize(requestToRead);
            } catch (JsonProcessingException e) {
                throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + requestToRead);
            }
        }
    }

    public static class DefaultResponseWriter<W extends DefaultResponseWriter<W>> implements AbstractInstruction.ResponseWriter<W> {

        protected Response responseToBuild;

        public DefaultResponseWriter(Response response) {
            this.responseToBuild = response;
        }

        @Override
        public W setResultCode(ResultCode resultCode) {
            responseToBuild.setResultCode(resultCode.getResultCode());
            return getThis();
        }

        @Override
        public W setResultMessage(String resultMessage) {
            responseToBuild.setResultMessage(resultMessage);
            return getThis();
        }

        @Override
        public W setParameters(Map<String, String> parameters) {
            responseToBuild.setParameters(parameters);
            return getThis();
        }

        @Override
        public W putParameter(String key, String value) {
            Map<String, String> parameters = responseToBuild.getParameters();
            if(parameters == null){
                parameters = new HashMap<>();
                responseToBuild.setParameters(parameters);
            }
            parameters.put(key, value);
            return getThis();
        }

        @Override
        public W addParameters(Map<String, String> parameters) {
            Map<String, String> parametersInResponse = responseToBuild.getParameters();
            if(parametersInResponse == null){
                responseToBuild.setParameters(parameters);
            }else{
                parametersInResponse.putAll(parameters);
            }
            return getThis();
        }

        @Override
        public final W getThis() {
            return (W) this;
        }

        public W setResultCodeAndResultMessage(ResultCode resultCode, String resultMessage){
            setResultCode(resultCode).setResultMessage(resultMessage);
            return getThis();
        }

        @Override
        public String toString(){
            try {
                return JsonUtils.serialize(responseToBuild);
            } catch (JsonProcessingException e) {
                throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + responseToBuild);
            }
        }
    }
}
