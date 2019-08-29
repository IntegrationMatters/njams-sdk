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
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;

/**
 * This abstract class represents the instruction that was sent to the client by the server. It holds a
 * {@link AbstractRequestReader AbstractRequestReader} to read the incoming request and a
 * {@link AbstractResponseWriter AbstractResponseWriter} to write a response to the processed request respectively.
 *
 * @param <R> The AbstractRequestReader to read the request from
 * @param <W> The AbstractResponseWriter to write the response to
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class AbstractInstruction<R extends AbstractRequestReader, W extends AbstractResponseWriter> implements Instruction {

    /**
     * Default prefix for parsing Exceptions.
     */
    public static final String UNABLE_TO_DESERIALZE_OBJECT = "Unable to deserialize: ";

    private static final ResultCode DEFAULT_SUCCESS_CODE = ResultCode.SUCCESS;

    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    protected R reader;

    protected W writer;

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    /**
     * Sets the underlying instruction
     *
     * @param messageFormatInstruction the instruction to set
     */
    public AbstractInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction) {
        this.messageFormatInstruction = messageFormatInstruction;
    }

    /**
     * Checks if the {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction instruction} to work with is
     * null.
     *
     * @return true, if instruction is null, else false
     */
    @Override
    public boolean isEmpty() {
        return messageFormatInstruction == null;
    }

    /**
     * This method returns the underlying instruction.
     *
     * @return the underlying {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction instruction} or null,
     * if {@link #isEmpty() isEmpty()} is true.
     */
    public com.faizsiegeln.njams.messageformat.v4.command.Instruction getRealInstruction() {
        return messageFormatInstruction;
    }

    /**
     * Returns the corresponding RequestReader to the {@link Instruction instruction}.
     *
     * @return instance of a RequestReader
     */
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

    /**
     * Creates the actual instance of a {@link AbstractRequestReader AbstractRequestReader}
     *
     * @param request the request that the {@link AbstractRequestReader AbstractRequestReader} will have to read from
     * @return a new {@link AbstractRequestReader AbstractRequestReader} that reads from the given request
     */
    protected abstract R createRequestReaderInstance(Request request);

    /**
     * Returns the corresponding AbstractResponseWriter to the {@link Instruction instruction}
     *
     * @return instance of a ResponseWriter
     */
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
        response.setResultCode(DEFAULT_SUCCESS_CODE.getResultCode());
        response.setResultMessage(DEFAULT_SUCCESS_MESSAGE);
        return response;
    }

    /**
     * Creates the actual instance of a {@link AbstractResponseWriter AbstractResponseWriter}
     *
     * @param response the response that the {@link AbstractResponseWriter AbstractResponseWriter} will write to
     * @return a new {@link AbstractResponseWriter AbstractResponseWriter} that writes to the given response
     */
    protected abstract W createResponseWriterInstance(Response response);

}
