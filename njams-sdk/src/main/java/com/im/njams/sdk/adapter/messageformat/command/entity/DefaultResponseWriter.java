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
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;

import java.util.HashMap;
import java.util.Map;

public class DefaultResponseWriter<W extends DefaultResponseWriter<W>> implements ResponseWriter<W> {

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
}
