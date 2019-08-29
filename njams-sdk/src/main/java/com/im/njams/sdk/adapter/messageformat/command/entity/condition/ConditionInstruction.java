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

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction;

/**
 * This class represents the instruction that was sent to the client by the server. It holds a
 * {@link ConditionRequestReader ConditionRequestReader} to read the incoming request and a
 * {@link ConditionResponseWriter ConditionResponseWriter} to write a response to the processed request respectively.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class ConditionInstruction extends AbstractInstruction<ConditionRequestReader, ConditionResponseWriter> {

    /**
     * Sets the underlying instruction
     *
     * @param messageFormatInstruction the instruction to set
     */
    public ConditionInstruction(Instruction messageFormatInstruction) {
        super(messageFormatInstruction);
    }

    /**
     * Creates the actual instance of a {@link ConditionRequestReader ConditionRequestReader}
     *
     * @param request the request that the {@link ConditionRequestReader ConditionRequestReader} will have to read from
     * @return a new {@link ConditionRequestReader ConditionRequestReader} that reads from the given request
     */
    @Override
    protected ConditionRequestReader createRequestReaderInstance(Request request) {
        return new ConditionRequestReader(request);
    }

    /**
     * Creates the actual instance of a {@link ConditionResponseWriter ConditionResponseWriter}
     *
     * @param response the response that the {@link ConditionResponseWriter ConditionResponseWriter} will write to
     * @return a new {@link ConditionResponseWriter ConditionResponseWriter} that writes to the given response
     */
    @Override
    protected ConditionResponseWriter createResponseWriterInstance(Response response) {
        return new ConditionResponseWriter(response);
    }

}
