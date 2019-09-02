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

import static com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT;

/**
 * This class provides methods to write the outgoing instruction's response.
 *
 * @param <W> The type of the NjamsResponseWriter for chaining the methods of the NjamsResponseWriter.
 * @author krautenberg
 * @version 4.1.0
 */
public class NjamsResponseWriter<W extends NjamsResponseWriter<W>> implements Instruction.ResponseWriter<W> {

    private Response responseToWrite;

    /**
     * Sets the underlying response
     *
     * @param responseToWriteTo the response to set
     */
    public NjamsResponseWriter(Response responseToWriteTo) {
        this.responseToWrite = responseToWriteTo;
    }

    /**
     * Checks if the underlying response is null or was only initialized.
     *
     * @return true, if underlying response is null or was only initialized, else false
     */
    @Override
    public boolean isEmpty() {
        boolean isEmpty = responseToWrite == null;
        if (!isEmpty) {
            isEmpty = isEmptyResponse();
        }
        return isEmpty;
    }

    private boolean isEmptyResponse() {
        boolean isResultCodeDefault = resultCodeHasntChanged();
        boolean isResultMessageDefault = resultMessageHasntChanged();
        boolean isDateTimeDefault = dateTimeHasntChanged();
        boolean areParametersDefault = parametersHaventChanged();
        return isResultCodeDefault && isResultMessageDefault && isDateTimeDefault && areParametersDefault ;
    }

    private boolean resultCodeHasntChanged() {
        return responseToWrite.getResultCode() == ResultCode.SUCCESS.getResultCode();
    }

    private boolean resultMessageHasntChanged() {
        return responseToWrite.getResultMessage() == null;
    }

    private boolean dateTimeHasntChanged() {
        return responseToWrite.getDateTime() == null;
    }

    private boolean parametersHaventChanged() {
        return responseToWrite.getParameters() == null ? false : responseToWrite
                .getParameters().isEmpty();
    }

    /**
     * Sets the {@link ResultCode ResultCode} to the {@link Response response}.
     *
     * @param resultCode the resultCode to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    @Override
    public W setResultCode(ResultCode resultCode) {
        responseToWrite.setResultCode(resultCode.getResultCode());
        return getThis();
    }

    /**
     * Sets the ResultMessage to the {@link Response response}
     *
     * @param resultMessage the resultMessage to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    @Override
    public W setResultMessage(String resultMessage) {
        responseToWrite.setResultMessage(resultMessage);
        return getThis();
    }

    /**
     * Sets the {@link ResultCode ResultCode} and the ResultMessage to the {@link Response response}.
     *
     * @param resultCode    the resultCode to set
     * @param resultMessage the resultMessage to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods
     */
    public W setResultCodeAndResultMessage(ResultCode resultCode, String resultMessage) {
        return setResultCode(resultCode).setResultMessage(resultMessage);
    }

    /**
     * Sets the {@link LocalDateTime} to the {@link Response response}.
     *
     * @param localDateTime the localDateTime to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    public W setDateTime(LocalDateTime localDateTime) {
        responseToWrite.setDateTime(localDateTime);
        return getThis();
    }

    /**
     * Puts a parameter key-value pair to the parameters of the {@link Response response}. A parameter with the
     * same key overwrites an old value that has been set before.
     *
     * @param key   the key of the key-value pair
     * @param value the value of the key-value pair
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    @Override
    public W putParameter(String key, String value) {
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
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    @Override
    public W setParameters(Map<String, String> parameters) {
        responseToWrite.setParameters(parameters);
        return getThis();
    }

    /**
     * Adds the given key-value pairs to the parameters in the {@link Response response}. The new parameters might
     * overwrite
     * old key-value pairs.
     *
     * @param parameters the parameters to add
     * @return itself via {@link #getThis() getThis()} for chaining NjamsResponseWriter methods.
     */
    @Override
    public W addParameters(Map<String, String> parameters) {
        Map<String, String> parametersInResponse = responseToWrite.getParameters();
        if (parametersInResponse == null) {
            responseToWrite.setParameters(parameters);
        } else {
            parametersInResponse.putAll(parameters);
        }
        return getThis();
    }

    /**
     * This method is a helper method for all other methods in the NjamsResponseWriter to refer to the correct
     * NjamsResponseWriter type.
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
            String toReturn = "null";
            if (!isEmpty()) {
                toReturn = JsonUtils.serialize(responseToWrite);
            }
            return toReturn;
        } catch (JsonProcessingException e) {
            throw new NjamsSdkRuntimeException(UNABLE_TO_DESERIALIZE_OBJECT + responseToWrite);
        }
    }
}
