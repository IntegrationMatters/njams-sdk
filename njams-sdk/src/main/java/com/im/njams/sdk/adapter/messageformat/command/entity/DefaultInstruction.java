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
import java.util.Map;

public class DefaultInstruction extends AbstractInstruction<DefaultInstruction.DefaultRequestReader, DefaultInstruction.DefaultResponseWriter> {

    public static final String UNABLE_TO_DESERIALZE_OBJECT = "Unable to deserialize: ";

    public DefaultInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    @Override
    protected DefaultRequestReader createRequestReaderInstance(Request request) {
        return new DefaultRequestReader(request);
    }

    @Override
    protected DefaultResponseWriter createResponseWriterInstance(Response response) {
        return new DefaultResponseWriter(response);
    }

    public static class DefaultRequestReader implements com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader {

        public static final String EMPTY_STRING = "";

        protected Request requestToRead;

        protected DefaultRequestReader(Request requestToRead) {
            this.requestToRead = requestToRead;
        }

        @Override
        public boolean isEmpty() {
            return requestToRead == null;
        }

        @Override
        public boolean isCommandNull() {
            return isEmpty() || requestToRead.getCommand() == null;
        }

        @Override
        public boolean isCommandEmpty() {
            return isCommandNull() || requestToRead.getCommand().equals(EMPTY_STRING);
        }

        @Override
        public String getCommand() {
            String foundCommand = EMPTY_STRING;
            if (!isCommandNull()) {
                foundCommand = requestToRead.getCommand();
            }
            return foundCommand;
        }

        @Override
        public Map<String, String> getParameters() {
            if (!isEmpty()) {
                return Collections.unmodifiableMap(requestToRead.getParameters());
            }
            return Collections.EMPTY_MAP;
        }

        @Override
        public String getParameter(String paramKey) {
            return getParameters().get(paramKey);
        }

        @Override
        public String toString() {
            try {
                return JsonUtils.serialize(requestToRead);
            } catch (JsonProcessingException e) {
                throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + requestToRead);
            }
        }
    }

    public static class DefaultResponseWriter<W extends DefaultResponseWriter<W>> extends AbstractInstruction.AbstractResponseWriter<W> {

        protected DefaultResponseWriter(Response response) {
            super(response);
        }

        public W setResultCodeAndResultMessage(ResultCode resultCode, String resultMessage) {
            setResultCode(resultCode).setResultMessage(resultMessage);
            return getThis();
        }
    }
}
