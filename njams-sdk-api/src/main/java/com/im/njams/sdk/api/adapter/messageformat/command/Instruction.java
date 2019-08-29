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

package com.im.njams.sdk.api.adapter.messageformat.command;

import java.util.Map;

/**
 * This interface represents the instruction that was sent to the client by the server. It holds a
 * {@link Instruction.RequestReader RequestReader} to read the incoming request and a
 * {@link Instruction.ResponseWriter ResponseWriter} to write a response to the processed request respectively.
 *
 * @param <R> The RequestReader to read the request from
 * @param <W> The ResponseWriter to write the response to
 * @author krautenberg
 * @version 4.1.0
 */
public interface Instruction<R extends Instruction.RequestReader, W extends Instruction.ResponseWriter> {

    /**
     * Checks if the instruction to work with is null.
     *
     * @return true, if instruction is null, else false
     */
    boolean isEmpty();

    /**
     * Returns the corresponding RequestReader to the instruction.
     *
     * @return instance of a RequestReader
     */
    R getRequestReader();

    /**
     * Returns the corresponding ResponseWriter to the instruction
     *
     * @return instance of a ResponseWriter
     */
    W getResponseWriter();

    /**
     * This interface provides methods to read the incoming instruction's request.
     */
    interface RequestReader {

        /**
         * Empty String means "".
         */
        String EMPTY_STRING = "";

        /**
         * Checks if the request to read is null.
         *
         * @return true, if request is null, else false
         */
        boolean isEmpty();

        /**
         * Checks if the command of the request is null.
         *
         * @return true, if command of request is null or {@link #isEmpty() RequestReader.isEmpty()} is true, else
         * false
         */
        boolean isCommandNull();

        /**
         * Checks if the command of the request is {@value #EMPTY_STRING}.
         *
         * @return true, if command of request is empty or {@link #isCommandNull() RequestReader.isCommandNull()} is
         * true, else false
         */
        boolean isCommandEmpty();

        /**
         * Returns the command of the request.
         *
         * @return Either the command if {@link #isCommandEmpty() isCommandEmpty()} is false or it returns
         * {@value #EMPTY_STRING}.
         */
        String getCommand();

        /**
         * Returns the request parameters of the request.
         *
         * @return the read-only request parameters of the request or an Empty immutable map.
         */
        Map<String, String> getParameters();

        /**
         * Returns the corresponding parameter for this parameter key.
         *
         * @param paramKey parameter key that may point to a parameter value.
         * @return the corresponding parameter value, otherwise null.
         */
        String getParameter(String paramKey);
    }

    /**
     * This interface provides methods to write the outgoing instruction's response.
     *
     * @param <W> The type of the ResponseWriter for chaining the methods of the ResponseWriter.
     */
    interface ResponseWriter<W extends ResponseWriter<W>> {

        /**
         * Checks if the response is null.
         *
         * @return true, if response is null, else false
         */
        boolean isEmpty();

        /**
         * Sets the {@link ResultCode ResultCode} to the response.
         *
         * @param resultCode the resultCode to set
         * @return itself via {@link #getThis() getThis()} for chaining ResponseWriter methods.
         */
        W setResultCode(ResultCode resultCode);

        /**
         * Sets the ResultMessage to the response
         *
         * @param resultMessage the resultMessage to set
         * @return itself via {@link #getThis() getThis()} for chaining ResponseWriter methods.
         */
        W setResultMessage(String resultMessage);

        /**
         * Puts a parameter key-value pair to the parameters of the response.
         *
         * @param key the key of the key-value pair
         * @param value the value of the key-value pair
         * @return itself via {@link #getThis() getThis()} for chaining ResponseWriter methods.
         */
        W putParameter(String key, String value);

        /**
         * Sets the given parameters to the response.
         *
         * @param parameters the parameters to set
         * @return itself via {@link #getThis() getThis()} for chaining ResponseWriter methods.
         */
        W setParameters(Map<String, String> parameters);

        /**
         * Adds the given key-value pairs to the parameters in the response.
         *
         * @param parameters the parameters to add
         * @return itself via {@link #getThis() getThis()} for chaining ResponseWriter methods.
         */
        W addParameters(Map<String, String> parameters);

        /**
         * This method is a helper method for all other methods in the ResponseWriter to refer to the correct
         * ResponseWriter type.
         *
         * @return this
         */
        W getThis();
    }
}
