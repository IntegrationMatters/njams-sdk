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
import com.im.njams.sdk.api.adapter.messageformat.command.entity.RequestReader;

import java.util.Collections;
import java.util.Map;

public class DefaultRequestReader implements RequestReader {

    public static final String EMPTY_STRING = "";

    protected Request requestToRead;

    public DefaultRequestReader(Request requestToRead) {
        this.requestToRead = requestToRead;
    }

    @Override
    public boolean isEmpty(){
        return requestToRead == null;
    }

    @Override
    public boolean isCommandNull(){
        return isEmpty() || requestToRead.getCommand() == null;
    }

    @Override
    public boolean isCommandEmpty(){
        return isCommandNull() || requestToRead.getCommand().equals(EMPTY_STRING);
    }

    @Override
    public String getCommand() {
        String foundCommand = EMPTY_STRING;
        if(!isCommandNull()){
            foundCommand = requestToRead.getCommand();
        }
        return foundCommand;
    }

    @Override
    public Map<String, String> getParameters() {
        if(!isEmpty()){
            return Collections.unmodifiableMap(requestToRead.getParameters());
        }
        return Collections.EMPTY_MAP;
    }

    @Override
    public String getParameter(String paramKey) {
        return getParameters().get(paramKey);
    }

}
