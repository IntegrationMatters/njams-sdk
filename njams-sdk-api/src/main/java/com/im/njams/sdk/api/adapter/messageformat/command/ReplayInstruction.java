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

package com.im.njams.sdk.api.adapter.messageformat.command;

/**
 * This interface represents the replay instruction that was sent to the client by the server. It holds a
 * {@link ReplayInstruction.RequestReader replayRequestReader} to read the incoming replay request and a
 * {@link ReplayInstruction.ResponseWriter replayResponseWriter} to write a response to the processed replay
 * respectively.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public interface ReplayInstruction<R extends Instruction.RequestReader, W extends Instruction.ResponseWriter> extends Instruction<R, W> {

    /**
     * This interface provides methods to read the incoming replay request.
     */
    interface ReplayRequestReader extends Instruction.RequestReader {

        /**
         * Returns the process that needs to be replayed.
         *
         * @return the process that needs to be replayed or null, if not set.
         */
        String getProcess();

        /**
         * Returns the start activity that needs to be replayed.
         *
         * @return the start activity that needs to be replayed or null, if not set.
         */
        String getStartActivity();

        /**
         * Returns the payload that needs to be replayed.
         *
         * @return the payload that needs to be replayed or null, if not set.
         */
        String getPayload();

        /**
         * Returns the deepTrace that was set in the request
         *
         * @return true, if deepTrace was set and set to true, otherwise false.
         */
        boolean isDeepTrace();

        /**
         * Returns if test is set in the request.
         *
         * @return true, if test was set and set to true, otherwise false.
         */
        boolean isTest();
    }

    /**
     * This interface provides methods to write the outgoing response for the processed replay.
     *
     * @param <W> The type of the ReplayResponseWriter for chaining the methods of the ReplayResponseWriter
     */
    interface ReplayResponseWriter<W extends ReplayResponseWriter<W>> extends Instruction.ResponseWriter<W> {

        /**
         * Sets the exception to the response.
         *
         * @param exception the exception to set
         * @return itself via {@link #getThis() getThis()} for chaining ReplayResponseWriter methods
         */
        ReplayResponseWriter setException(String exception);

        /**
         * Sets the mainLogId to the response
         *
         * @param mainLogId the mainLogId to set
         * @return itself via {@link #getThis() getThis()} for chaining ReplayResponseWriter methods
         */
        ReplayResponseWriter setMainLogId(String mainLogId);
    }
}
