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
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.AbstractProcessorTemplate;

/**
 * Todo: Write Doc
 */
public class SendProjectMessageProcessor extends AbstractProcessorTemplate {

    static final String SUCCESS_RESULT_MESSAGE = "Successfully send ProjectMessage via NjamsClient";

    private Njams njams;

    public SendProjectMessageProcessor(Njams njams) {
        this.njams = njams;
    }

    @Override
    public String getCommandToListenTo(){
        return Command.SEND_PROJECTMESSAGE.commandString();
    }

    @Override
    protected void process() {
        njams.flushResources();
        setSuccessResponse();
    }

    private void setSuccessResponse() {
        ((NjamsResponseWriter)getResponseWriter()).setResultCodeAndResultMessage(ResultCode.SUCCESS, SUCCESS_RESULT_MESSAGE);
    }
}
