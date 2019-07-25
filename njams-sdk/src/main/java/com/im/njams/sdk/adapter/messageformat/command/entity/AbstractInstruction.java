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
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.RequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;

public abstract class AbstractInstruction<R extends RequestReader, W extends ResponseWriter> implements Instruction<R, W> {

    protected com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    protected R reader;

    protected W writer;

    public AbstractInstruction(com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction) {
        this.messageFormatInstruction = messageFormatInstruction;
    }

    @Override
    public R getRequestReader() {
        if(reader == null){
            if(!isEmpty()){
                reader = createRequestReaderInstance();
            }
        }
        return reader;
    }

    protected abstract R createRequestReaderInstance();

    @Override
    public W getResponseWriter() {
        if(writer == null){
            if(!isEmpty()){
                Response responseToWriteTo = messageFormatInstruction.getResponse();
                if(responseToWriteTo == null){
                    responseToWriteTo = new Response();
                    messageFormatInstruction.setResponse(responseToWriteTo);
                }
                writer = createResponseWriterInstance();
            }
        }
        return writer;
    }

    protected abstract W createResponseWriterInstance();

    public boolean isEmpty(){
        return messageFormatInstruction == null;
    }

    public com.faizsiegeln.njams.messageformat.v4.command.Instruction getRealInstruction(){
        return messageFormatInstruction;
    }
}
