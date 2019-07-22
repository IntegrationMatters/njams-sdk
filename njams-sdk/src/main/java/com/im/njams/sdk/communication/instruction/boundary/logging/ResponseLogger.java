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

package com.im.njams.sdk.communication.instruction.boundary.logging;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class ResponseLogger implements InstructionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(
            ResponseLogger.class);

    private JsonSerializer<Response> responseSerializer = new JsonSerializer<>();

    @Override
    public void log(Instruction instruction) {
        Request processedRequest = instruction.getRequest();
        Response responseToLog = instruction.getResponse();
        final int responseResultCode = responseToLog.getResultCode();
        if (processedRequest != null) {
            logResponseForProcessedRequest(processedRequest, responseToLog, responseResultCode);
        } else {
            logResponseForInvalidInstruction(responseToLog, responseResultCode);
        }
    }

    private void logResponseForProcessedRequest(Request processedRequest, Response responseToLog, int responseResultCode) {
        String commandOfRequest = processedRequest.getCommand();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created response for command {} with result code: {}", commandOfRequest, responseResultCode);
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Response for command {} : \n{}", commandOfRequest, responseSerializer.serialize(responseToLog));
            } catch (Exception responseNotSerializableException) {
                LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
            }
        }
    }

    private void logResponseForInvalidInstruction(Response responseToLog, int responseResultCode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created response with result code {} for no forwarded request.", responseResultCode);
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Response for a not forwarded request {} : \n{}", responseSerializer.serialize(responseToLog));
            } catch (Exception responseNotSerializableException) {
                LOG.error("Response couldn't be serialized successfully", responseNotSerializableException);
            }
        }
    }
}
