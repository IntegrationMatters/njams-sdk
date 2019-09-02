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
import com.im.njams.sdk.adapter.messageformat.command.control.RequestReaderFactory;
import com.im.njams.sdk.adapter.messageformat.command.control.ResponseWriterFactory;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;

/**
 * This class represents the instruction that was sent to the client by the server. It holds a
 * {@link NjamsRequestReader NjamsRequestReader} to read the incoming request and a
 * {@link NjamsResponseWriter NjamsResponseWriter} to write a response to the processed request respectively.
 *
 * @param <R> The NjamsRequestReader to read the request from
 * @param <W> The NjamsResponseWriter to write the response to
 * @author krautenberg
 * @version 4.1.0
 */
public class NjamsInstruction<R extends NjamsRequestReader, W extends NjamsResponseWriter> implements Instruction {

    /**
     * Default prefix for parsing Exceptions.
     */
    public static final String UNABLE_TO_DESERIALIZE_OBJECT = "Unable to deserialize: ";

    private R reader;

    private W writer;

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    private Request requestToReadFrom;

    private Response responseToWriteTo;

    /**
     * Sets the underlying instruction and creates a {@link NjamsRequestReader NjamsRequestReader} of actual type
     * {@link NjamsRequestReader NjamsRequestReader}
     * and a {@link NjamsResponseWriter NjamsResponseWriter} of type {@link NjamsResponseWriter NjamsResponseWriter}.
     *
     * @param messageFormatInstruction the instruction to read from and write to
     */
    public NjamsInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction) {
        this(messageFormatInstruction, (Class<R>) NjamsRequestReader.class, (Class<W>) NjamsResponseWriter.class);
    }

    /**
     * Sets the underlying instruction and creates a {@link NjamsRequestReader NjamsRequestReader} of type {@link R R}
     * and a {@link NjamsResponseWriter NjamsResponseWriter} of type {@link W W}. If the classes can't be initialized
     * correctly, {@link NjamsRequestReader NjamsRequestReader} will be default RequestReader and
     * {@link NjamsResponseWriter NjamsResponseWriter} will be default Response Writer.
     *
     * @param messageFormatInstruction the instruction to read from and write to
     * @param readerClass              the actual type to read the request from
     * @param writerClass              the actual type to write the response to
     */
    public NjamsInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction,
            Class<R> readerClass, Class<W> writerClass) {
        this.messageFormatInstruction = messageFormatInstruction;
        if (!isEmpty()) {
            this.requestToReadFrom = getRequestToReadFrom();
            this.responseToWriteTo = getResponseToWriteTo();
        }
        this.reader = RequestReaderFactory.create(requestToReadFrom, readerClass);
        this.writer = ResponseWriterFactory.create(responseToWriteTo, writerClass);
    }

    private Request getRequestToReadFrom() {
        return messageFormatInstruction.getRequest();
    }

    private Response getResponseToWriteTo() {
        Response responseOfMessageFormat = messageFormatInstruction.getResponse();
        if (responseOfMessageFormat == null) {
            responseOfMessageFormat = new Response();
            messageFormatInstruction.setResponse(responseOfMessageFormat);
        }
        return responseOfMessageFormat;
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
     * Returns the underlying instruction. To modify the instruction it is recommended to use the
     * {@link W ResponseWriter} via {@link #getResponseWriter() getResponseWriter()}
     *
     * @return the underlying {@link com.faizsiegeln.njams.messageformat.v4.command.Instruction instruction} or null,
     * if {@link #isEmpty() isEmpty()} is true.
     */
    public com.faizsiegeln.njams.messageformat.v4.command.Instruction getRealInstruction() {
        return messageFormatInstruction;
    }

    /**
     * Returns the corresponding NjamsRequestReader to the {@link Instruction instruction}.
     *
     * @return instance of a RequestReader
     */
    @Override
    public R getRequestReader() {
        return reader;
    }

    /**
     * Returns the corresponding NjamsResponseWriter to the {@link Instruction instruction}
     *
     * @return instance of a ResponseWriter
     */
    @Override
    public W getResponseWriter() {
        return writer;
    }
}
