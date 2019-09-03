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
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionResponseWriter;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayResponseWriter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ResponseWriterFactoryTest {

    private Instruction instructionMock;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
    }

    @Test
    public void createNjamsResponseWriter() {
        NjamsResponseWriter responseWriter = ResponseWriterFactory.create(instructionMock, NjamsResponseWriter.class);

        assertTrue(responseWriter instanceof NjamsResponseWriter);
        assertFalse(responseWriter instanceof NjamsReplayResponseWriter);
        assertFalse(responseWriter instanceof ConditionResponseWriter);
    }

    @Test
    public void createReplayResponseWriter() {
        NjamsResponseWriter responseWriter = ResponseWriterFactory.create(instructionMock, NjamsReplayResponseWriter.class);

        assertTrue(responseWriter instanceof NjamsResponseWriter);
        assertTrue(responseWriter instanceof NjamsReplayResponseWriter);
        assertFalse(responseWriter instanceof ConditionResponseWriter);
    }

    @Test
    public void createConditionResponseWriter() {
        NjamsResponseWriter responseWriter = ResponseWriterFactory.create(instructionMock, ConditionResponseWriter.class);

        assertTrue(responseWriter instanceof NjamsResponseWriter);
        assertFalse(responseWriter instanceof NjamsReplayResponseWriter);
        assertTrue(responseWriter instanceof ConditionResponseWriter);
    }

    @Test
    public void createResponseWriterImpl() {
        NjamsResponseWriter responseWriter = ResponseWriterFactory.create(instructionMock, ResponseWriterImpl.class);

        assertTrue(responseWriter instanceof NjamsResponseWriter);
        assertFalse(responseWriter instanceof NjamsReplayResponseWriter);
        assertFalse(responseWriter instanceof ConditionResponseWriter);
        assertTrue(responseWriter instanceof ResponseWriterImpl);
    }

    private static class ResponseWriterImpl extends NjamsResponseWriter {

        public ResponseWriterImpl(Instruction instructionToWriteTo) {
            super(instructionToWriteTo);
        }
    }
}