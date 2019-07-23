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

import com.im.njams.sdk.api.adapter.messageformat.command.Response;

import java.util.Map;

public class NjamsResponse implements Response {

    private com.faizsiegeln.njams.messageformat.v4.command.Response messageFormatResponse;

    public NjamsResponse(com.faizsiegeln.njams.messageformat.v4.command.Response messageFormatResponse){
        this.messageFormatResponse = messageFormatResponse;
    }

    @Override
    public void setResultCode(ResultCode resultCode) {
        messageFormatResponse.setResultCode(resultCode.getResultCode());
    }

    @Override
    public void setResultMessage(String resultMessage) {
        messageFormatResponse.setResultMessage(resultMessage);
    }

    @Override
    public void putParameter(String key, String value) {
        messageFormatResponse.getParameters().put(key, value);
    }

    @Override
    public void addParameters(Map<String, String> parameters) {
        messageFormatResponse.getParameters().putAll(parameters);
    }
}
