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

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayRequestReader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestReaderFactoryTest {

    private static final String COMMAND = "Command";

    private Request requestMock;

    @Before
    public void initialize() {
        requestMock = mock(Request.class);
        when(requestMock.getCommand()).thenReturn(COMMAND);
    }

    @Test
    public void createNjamsRequestReader() {
        NjamsRequestReader requestReader = RequestReaderFactory.create(requestMock, NjamsRequestReader.class);
        assertEquals(requestReader.getCommand(), COMMAND);

        assertTrue(requestReader instanceof NjamsRequestReader);
        assertFalse(requestReader instanceof NjamsReplayRequestReader);
        assertFalse(requestReader instanceof ConditionRequestReader);
    }

    @Test
    public void createReplayRequestReader() {
        NjamsRequestReader requestReader = RequestReaderFactory.create(requestMock, NjamsReplayRequestReader.class);
        assertEquals(requestReader.getCommand(), COMMAND);

        assertTrue(requestReader instanceof NjamsRequestReader);
        assertTrue(requestReader instanceof NjamsReplayRequestReader);
        assertFalse(requestReader instanceof ConditionRequestReader);
    }

    @Test
    public void createConditionRequestReader() {
        NjamsRequestReader requestReader = RequestReaderFactory.create(requestMock, ConditionRequestReader.class);
        assertEquals(requestReader.getCommand(), COMMAND);

        assertTrue(requestReader instanceof NjamsRequestReader);
        assertFalse(requestReader instanceof NjamsReplayRequestReader);
        assertTrue(requestReader instanceof ConditionRequestReader);
    }

    @Test
    public void createRequestReaderImpl() {
        NjamsRequestReader requestReader = RequestReaderFactory.create(requestMock, RequestReaderImpl.class);
        assertEquals(requestReader.getCommand(), COMMAND);

        assertTrue(requestReader instanceof NjamsRequestReader);
        assertFalse(requestReader instanceof NjamsReplayRequestReader);
        assertFalse(requestReader instanceof ConditionRequestReader);
        assertTrue(requestReader instanceof RequestReaderImpl);
    }

    private static class RequestReaderImpl extends NjamsRequestReader {

        /**
         * Sets the underlying request
         *
         * @param requestToReadFrom the request to set
         */
        public RequestReaderImpl(Request requestToReadFrom) {
            super(requestToReadFrom);
        }
    }

}