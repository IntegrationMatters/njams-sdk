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

package com.im.njams.sdk.adapter.messageformat.command.entity.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction;

/**
 * This class represents the instruction that was sent to the client by the server. It holds a
 * {@link ReplayRequestReader ReplayRequestReader} to read the incoming request and a
 * {@link ReplayResponseWriter ReplayResponseWriter} to write a response to the processed request respectively.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class ReplayInstruction extends AbstractInstruction<ReplayRequestReader, ReplayResponseWriter> {

    /**
     * Sets the underlying instruction
     *
     * @param messageFormatInstruction the instruction to set
     */
    public ReplayInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    /**
     * Creates the actual instance of a {@link ReplayRequestReader ReplayRequestReader}
     *
     * @param request the request that the {@link ReplayRequestReader ReplayRequestReader} will have to read from
     * @return a new {@link ReplayRequestReader ReplayRequestReader} that reads from the given request
     */
    @Override
    protected ReplayRequestReader createRequestReaderInstance(Request request) {
        return new ReplayRequestReader(request);
    }

    /**
     * Creates the actual instance of a {@link ReplayResponseWriter ReplayResponseWriter}
     *
     * @param response the response that the {@link ReplayResponseWriter ReplayResponseWriter} will write to
     * @return a new {@link ReplayResponseWriter ReplayResponseWriter} that writes to the given response
     */
    @Override
    protected ReplayResponseWriter createResponseWriterInstance(Response response) {
        return new ReplayResponseWriter(response);
    }

}
