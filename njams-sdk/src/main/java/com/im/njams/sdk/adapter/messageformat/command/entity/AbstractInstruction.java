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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction.UNABLE_TO_DESERIALZE_OBJECT;

public abstract class AbstractInstruction<R extends Instruction.RequestReader,
        W extends AbstractInstruction.AbstractResponseWriter> implements Instruction {

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    public static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    protected R reader;

    protected W writer;

    public AbstractInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction) {
        this.messageFormatInstruction = messageFormatInstruction;
    }

    @Override
    public boolean isEmpty() {
        return messageFormatInstruction == null;
    }

    public com.faizsiegeln.njams.messageformat.v4.command.Instruction getRealInstruction() {
        return messageFormatInstruction;
    }

    @Override
    public R getRequestReader() {
        if (reader == null) {
            if (!isEmpty()) {
                reader = createRequestReaderInstance(messageFormatInstruction.getRequest());
            } else {
                reader = createRequestReaderInstance(null);
            }
        }
        return reader;
    }

    protected abstract R createRequestReaderInstance(Request request);

    @Override
    public W getResponseWriter() {
        if (writer == null) {
            if (!isEmpty()) {
                Response response = messageFormatInstruction.getResponse();
                if (response == null) {
                    createDefaultSuccessResponseButLetItSeemToBeEmpty();
                } else {
                    writer = createResponseWriterInstance(response);
                }
            } else {
                writer = createResponseWriterInstance(null);
            }
        }
        return writer;
    }

    /**
     * This method is used because if there is an instruction without a current response, the
     * responseWriter would have to create an response object by itself as soon as some method of the writer
     * would be invoked. This must be set to the instruction after that, but it couldn't because the ResponseWriters
     * don't know about the instruction itself.
     *
     * @return a newly created MessageFormat Response, with default values.
     */
    private Response createDefaultSuccessResponseButLetItSeemToBeEmpty() {
        Response response = createDefaultSuccessResponse();

        messageFormatInstruction.setResponse(response);
        writer = createResponseWriterInstance(response);
        writer.setResponseIsActuallyEmpty();
        return response;
    }

    private Response createDefaultSuccessResponse() {
        Response response = new Response();
        response.setResultCode(ResultCode.SUCCESS.getResultCode());
        response.setResultMessage(DEFAULT_SUCCESS_MESSAGE);
        return response;
    }

    protected abstract W createResponseWriterInstance(Response response);

    public static class AbstractResponseWriter<W extends AbstractResponseWriter<W>> implements Instruction.ResponseWriter<W> {

        private Response responseToBuild;

        private boolean isActuallyEmpty = false;

        protected AbstractResponseWriter(Response response) {
            this.responseToBuild = response;
        }

        @Override
        public boolean isEmpty() {
            return responseToBuild == null || isActuallyEmpty;
        }

        @Override
        public W setResultCode(ResultCode resultCode) {
            responseIsFilled();
            responseToBuild.setResultCode(resultCode.getResultCode());
            return getThis();
        }

        @Override
        public W setResultMessage(String resultMessage) {
            responseIsFilled();
            responseToBuild.setResultMessage(resultMessage);
            return getThis();
        }

        @Override
        public W setParameters(Map<String, String> parameters) {
            responseIsFilled();
            responseToBuild.setParameters(parameters);
            return getThis();
        }

        public W setDateTime(LocalDateTime dateTime) {
            responseIsFilled();
            responseToBuild.setDateTime(dateTime);
            return getThis();
        }

        @Override
        public W putParameter(String key, String value) {
            responseIsFilled();
            Map<String, String> parameters = responseToBuild.getParameters();
            if (parameters == null) {
                parameters = new HashMap<>();
                responseToBuild.setParameters(parameters);
            }
            parameters.put(key, value);
            return getThis();
        }

        @Override
        public W addParameters(Map<String, String> parameters) {
            responseIsFilled();
            Map<String, String> parametersInResponse = responseToBuild.getParameters();
            if (parametersInResponse == null) {
                responseToBuild.setParameters(parameters);
            } else {
                parametersInResponse.putAll(parameters);
            }
            return getThis();
        }

        public void setResponseIsActuallyEmpty() {
            isActuallyEmpty = true;
        }

        private void responseIsFilled() {
            isActuallyEmpty = false;
        }

        @Override
        public final W getThis() {
            return (W) this;
        }

        @Override
        public String toString() {
            try {
                return JsonUtils.serialize(responseToBuild);
            } catch (JsonProcessingException e) {
                throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + responseToBuild);
            }
        }
    }
}
