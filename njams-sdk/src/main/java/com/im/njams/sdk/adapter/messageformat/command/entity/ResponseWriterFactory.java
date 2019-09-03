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

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * This class provides a method to create {@link NjamsResponseWriter NjamsResponseWriters}.
 *
 * @author krautenberg
 * @version 4.1.0
 */
class ResponseWriterFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseWriterFactory.class);

    /**
     * Creates a {@link W NjamsResponseWriter} or an extended instance of {@link NjamsResponseWriter}. It tries
     * to create an instance of the given {@link Class<W> writerClass}. If it doesn't work, a
     * {@link NjamsResponseWriter NjamsResponseWriter} will be returned.
     *
     * @param instructionToWriteTo the instruction to set in the created {@link W NjamsResponseWriter}.
     * @param writerClass       the class to initialize the {@link NjamsResponseWriter NjamsResponseWriter}.
     * @param <W>               The type of class to initialize and to return.
     * @return either a correctly instantiated {@link NjamsResponseWriter NjamsResponseWriter} of type {@link W W} or
     * a default {@link NjamsResponseWriter NjamsResponseWriter} if the actual object of type {@link W W} couldn't be
     * created.
     */
    public static <W extends NjamsResponseWriter> W create(Instruction instructionToWriteTo, Class<W> writerClass) {
        W responseWriter;
        try {
            Constructor constructorOfResponseWriter = writerClass.getConstructor(Instruction.class);
            constructorOfResponseWriter.setAccessible(true);
            responseWriter = (W) constructorOfResponseWriter.newInstance(instructionToWriteTo);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException couldntCreateObject) {
            responseWriter = (W) new NjamsResponseWriter<>(instructionToWriteTo);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Creating a {} as default ResponseWriter. Cause: {}",
                        responseWriter.getClass().getSimpleName(), couldntCreateObject);
            }
        }
        return responseWriter;
    }
}
