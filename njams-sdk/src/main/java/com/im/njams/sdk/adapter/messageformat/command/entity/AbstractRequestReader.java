/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;

import java.util.Collections;
import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT;

/**
 * This class provides methods to read the incoming instruction's request.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class AbstractRequestReader implements Instruction.RequestReader {

    private Request requestToRead;

    /**
     * Sets the underlying request
     *
     * @param requestToRead the request to set
     */
    protected AbstractRequestReader(Request requestToRead) {
        this.requestToRead = requestToRead;
    }

    /**
     * Checks if the underlying request is null
     *
     * @return true, if underlying request is null, else false
     */
    @Override
    public boolean isEmpty() {
        return requestToRead == null;
    }

    /**
     * Checks if the command is null
     *
     * @return true, if either {@link #isEmpty() isEmpty()} is true or the command is null
     */
    @Override
    public boolean isCommandNull() {
        return isEmpty() || requestToRead.getCommand() == null;
    }

    /**
     * Checks if the command is empty ({@value #EMPTY_STRING})
     *
     * @return true, if either {@link #isCommandNull()} is true or command is {@value #EMPTY_STRING}
     */
    @Override
    public boolean isCommandEmpty() {
        return isCommandNull() || requestToRead.getCommand().equals(EMPTY_STRING);
    }

    /**
     * Returns the command or {@value #EMPTY_STRING}
     *
     * @return the command of the underlying request or {@value #EMPTY_STRING} if not found.
     */
    @Override
    public String getCommand() {
        String foundCommand = EMPTY_STRING;
        if (!isCommandNull()) {
            foundCommand = requestToRead.getCommand();
        }
        return foundCommand;
    }

    /**
     * Returns an read-only map of the underlying request parameters.
     *
     * @return read-only request parameters or an read-only empty map if not found.
     */
    @Override
    public Map<String, String> getParameters() {
        if (!isEmpty()) {
            return Collections.unmodifiableMap(requestToRead.getParameters());
        }
        return Collections.EMPTY_MAP;
    }

    /**
     * Returns the parameters value to the given key.
     *
     * @param paramKey parameter key that may point to a parameter value.
     * @return the value of the parameter or null if not found.
     */
    @Override
    public String getParameter(String paramKey) {
        return getParameters().get(paramKey);
    }

    /**
     * Returns the request as JSON.
     *
     * @return the underlying {@link Request request} as Json.
     */
    @Override
    public String toString() {
        try {
            return JsonUtils.serialize(requestToRead);
        } catch (JsonProcessingException e) {
            throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + requestToRead);
        }
    }
}
