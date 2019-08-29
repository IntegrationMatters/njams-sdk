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
 *
 */

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction.UNABLE_TO_DESERIALZE_OBJECT;

/**
 * This abstract class provides methods to write the outgoing instruction's response.
 *
 * @param <W> The type of the AbstractResponseWriter for chaining the methods of the AbstractResponseWriter.
 * @author krautenberg
 * @version 4.1.0
 */
public abstract class AbstractResponseWriter<W extends AbstractResponseWriter<W>> implements Instruction.ResponseWriter<W> {

    /**
     * The Response should be private because subclasses of the AbstractResponseWriter should use the provided
     * methods of this class to interact with the response.
     */
    private Response responseToWrite;

    private boolean isUnchangedResponse = false;

    /**
     * Sets the underlying response
     *
     * @param responseToWrite the response to set
     */
    protected AbstractResponseWriter(Response responseToWrite) {
        this.responseToWrite = responseToWrite;
    }

    /**
     * Checks if the underlying response is null or if it is not null, if it has been changed by this instance.
     * WARNING: This only works if you use the provided methods of this class. If you change
     * the underlying response without this instance, isEmpty() might return true instead of false
     * because it is NOT checked if the underlying response changed at all.
     *
     * @return true, if underlying response is not null and hasn't changed by this instance, else false
     */
    @Override
    public boolean isEmpty() {
        boolean isEmpty = isUnderlyingResponseNull();
        if (!isEmpty) {
            isEmpty = hasNotBeenChanged();
        }
        return isEmpty;
    }

    private boolean isUnderlyingResponseNull() {
        return responseToWrite == null;
    }

    private boolean hasNotBeenChanged() {
        return isUnchangedResponse;
    }

    /**
     * Sets the {@link ResultCode ResultCode} to the {@link Response response}.
     *
     * @param resultCode the resultCode to set
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    @Override
    public W setResultCode(ResultCode resultCode) {
        responseChanged();
        responseToWrite.setResultCode(resultCode.getResultCode());
        return getThis();
    }

    /**
     * Sets the ResultMessage to the {@link Response response}
     *
     * @param resultMessage the resultMessage to set
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    @Override
    public W setResultMessage(String resultMessage) {
        responseChanged();
        responseToWrite.setResultMessage(resultMessage);
        return getThis();
    }

    /**
     * Sets the {@link LocalDateTime} to the {@link Response response}.
     *
     * @param localDateTime the localDateTime to set
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    public W setDateTime(LocalDateTime localDateTime) {
        responseChanged();
        responseToWrite.setDateTime(localDateTime);
        return getThis();
    }

    /**
     * Puts a parameter key-value pair to the parameters of the {@link Response response}. A parameter with the
     * same key overwrites an old value that has been set before.
     *
     * @param key   the key of the key-value pair
     * @param value the value of the key-value pair
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    @Override
    public W putParameter(String key, String value) {
        responseChanged();
        Map<String, String> parameters = responseToWrite.getParameters();
        if (parameters == null) {
            parameters = new HashMap<>();
            responseToWrite.setParameters(parameters);
        }
        parameters.put(key, value);
        return getThis();
    }

    /**
     * Sets the given parameters to the {@link Response response}.
     *
     * @param parameters the parameters to set
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    @Override
    public W setParameters(Map<String, String> parameters) {
        responseChanged();
        responseToWrite.setParameters(parameters);
        return getThis();
    }

    /**
     * Adds the given key-value pairs to the parameters in the {@link Response response}. The new parameters might
     * overwrite
     * old key-value pairs.
     *
     * @param parameters the parameters to add
     * @return itself via {@link #getThis() getThis()} for chaining AbstractResponseWriter methods.
     */
    @Override
    public W addParameters(Map<String, String> parameters) {
        responseChanged();
        Map<String, String> parametersInResponse = responseToWrite.getParameters();
        if (parametersInResponse == null) {
            responseToWrite.setParameters(parameters);
        } else {
            parametersInResponse.putAll(parameters);
        }
        return getThis();
    }

    /**
     * Sets the {@link #isUnchangedResponse isUnchangedResponse} to true.
     */
    void setResponseIsActuallyEmpty() {
        isUnchangedResponse = true;
    }

    private void responseChanged() {
        isUnchangedResponse = false;
    }

    /**
     * This method is a helper method for all other methods in the AbstractResponseWriter to refer to the correct
     * AbstractResponseWriter type.
     *
     * @return this
     */
    @Override
    public final W getThis() {
        return (W) this;
    }

    /**
     * Returns the response as JSON.
     *
     * @return the underlying {@link Response response} as Json.
     */
    @Override
    public String toString() {
        try {
            return JsonUtils.serialize(responseToWrite);
        } catch (JsonProcessingException e) {
            throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALZE_OBJECT + responseToWrite);
        }
    }
}
