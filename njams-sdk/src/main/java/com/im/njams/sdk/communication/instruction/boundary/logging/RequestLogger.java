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
import com.im.njams.sdk.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class RequestLogger implements InstructionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(
            RequestLogger.class);

    private JsonSerializer<Object> requestSerializer = new JsonSerializer<>();

    @Override
    public void log(Instruction instruction) {
        Request requestToLog = instruction.getRequest();
        if (LOG.isDebugEnabled() && requestToLog != null) {
            LOG.debug("Received request with command: {}", requestToLog.getCommand());
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Request: \n{}", requestSerializer.serialize(requestToLog));
            } catch (Exception requestNotSerializableException) {
                LOG.error("Request couldn't be serialized successfully", requestNotSerializableException);
            }
        }
    }
}
