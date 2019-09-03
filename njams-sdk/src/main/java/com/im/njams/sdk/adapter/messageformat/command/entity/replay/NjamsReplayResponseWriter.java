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

package com.im.njams.sdk.adapter.messageformat.command.entity.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.ReplayInstruction;

/**
 * This class provides methods to write the outgoing instruction's response.
 *
 * @author krautenberg
 * @version 4.1.0
 */
public class NjamsReplayResponseWriter extends NjamsResponseWriter<NjamsReplayResponseWriter> implements ReplayInstruction.ReplayResponseWriter<NjamsReplayResponseWriter> {

    private static final String EXCEPTION = "Exception";

    private static final String MAIN_LOG_ID = "MainLogId";

    /**
     * Sets the underlying response
     *
     * @param responseToWriteTo the response to set
     */
    public NjamsReplayResponseWriter(Response responseToWriteTo) {
        super(responseToWriteTo);
    }

    /**
     * Sets the parameter value for the key {@value #EXCEPTION} with the given string to the {@link Response response}.
     *
     * @param exception the exception to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsReplayResponseWriter methods
     */
    @Override
    public NjamsReplayResponseWriter setException(String exception) {
        return putParameter(EXCEPTION, exception);
    }

    /**
     * Sets the parameter value for the key {@value #MAIN_LOG_ID} with the given string to the {@link Response response}.
     *
     * @param mainLogId the mainLogId to set
     * @return itself via {@link #getThis() getThis()} for chaining NjamsReplayResponseWriter methods
     */
    @Override
    public NjamsReplayResponseWriter setMainLogId(String mainLogId) {
        return putParameter(MAIN_LOG_ID, mainLogId);
    }
}
