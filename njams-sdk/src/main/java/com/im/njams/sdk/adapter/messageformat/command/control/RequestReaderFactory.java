/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.control;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsRequestReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class RequestReaderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RequestReaderFactory.class);

    public static <R extends NjamsRequestReader> R create(Request requestToReadFrom, Class<R> readerClass) {
        R requestReader;
        try {
            Constructor constructorOfRequestReader = readerClass.getConstructor(Request.class);
            constructorOfRequestReader.setAccessible(true);
            requestReader = (R) constructorOfRequestReader.newInstance(requestToReadFrom);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException couldntCreateObject) {
            requestReader = (R) new NjamsRequestReader(requestToReadFrom);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Creating a {} as default RequestReader. Cause: {}", requestReader.getClass().getSimpleName(),
                        couldntCreateObject);
            }
        }
        return requestReader;
    }
}
