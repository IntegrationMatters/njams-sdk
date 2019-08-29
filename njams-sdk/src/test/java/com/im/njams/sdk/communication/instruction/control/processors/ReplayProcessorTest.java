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
package com.im.njams.sdk.communication.instruction.control.processors;

import com.im.njams.sdk.adapter.messageformat.command.entity.replay.ReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.ReplayRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.ReplayResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import org.junit.Before;
import org.junit.Test;

import static com.im.njams.sdk.communication.instruction.control.processors.ReplayProcessor.EXCEPTION_WAS_THROWN_WHILE_REPLAYING;
import static com.im.njams.sdk.communication.instruction.control.processors.ReplayProcessor.REPLAY_PLUGIN_NOT_SET;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class ReplayProcessorTest {

    private static final String RUNTIME_EXCEPTION_MESSAGE = "Exception";

    private ReplayProcessor replayProcessor;

    private ReplayPlugin replayPluginMock;

    private ReplayHandler replayHandlerMock;

    private ReplayInstruction replayInstruction;

    private ReplayRequestReader replayRequestReaderMock;

    private ReplayResponseWriter replayResponseWriterMock;

    private RuntimeException runtimeExceptionMock;

    @Before
    public void initialize() {
        replayPluginMock = mock(ReplayPlugin.class);
        replayHandlerMock = mock(ReplayHandler.class);
        when(replayPluginMock.getPluginItem()).thenReturn(replayHandlerMock);

        replayProcessor = spy(new ReplayProcessor(replayPluginMock));

        replayInstruction = mock(ReplayInstruction.class);
        replayRequestReaderMock = mock(ReplayRequestReader.class);
        replayResponseWriterMock = mock(ReplayResponseWriter.class);
        when(replayResponseWriterMock.setResultCodeAndResultMessage(any(), any())).thenReturn(replayResponseWriterMock);

        doReturn(replayInstruction).when(replayProcessor).getInstruction();
        doReturn(replayRequestReaderMock).when(replayProcessor).getReplayRequestReader();
        doReturn(replayResponseWriterMock).when(replayProcessor).getReplayResponseWriter();

        runtimeExceptionMock = mock(RuntimeException.class);
        when(runtimeExceptionMock.getMessage()).thenReturn(RUNTIME_EXCEPTION_MESSAGE);
    }

//CanReplay tests

    @Test
    public void canReplayIfReplayHandlerIsSet() {
        canReplayProcessorReplay(true);
    }

    @Test
    public void cantReplayIfReplayHandlerIsNotSet() {
        canReplayProcessorReplay(false);
    }


    private void canReplayProcessorReplay(boolean isReplayHandlerSet) {
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(isReplayHandlerSet);
        boolean canReplay = replayProcessor.canReplay();
        assertEquals(isReplayHandlerSet, canReplay);
    }

//ProcessReplayInstruction tests

    @Test
    public void setStatusToReplayHandlerNotSetIfReplayHandlerIsNotSet() {
        replayProcessor.processReplayInstruction();
        verify(replayHandlerMock).replay(replayInstruction);
    }

//SetExceptionResponse tests

    @Test
    public void setExceptionResponse() {
        replayProcessor.setExceptionResponse(runtimeExceptionMock);
        verify(replayResponseWriterMock).setResultCodeAndResultMessage(ResultCode.ERROR,
                EXCEPTION_WAS_THROWN_WHILE_REPLAYING + RUNTIME_EXCEPTION_MESSAGE);
        verify(replayResponseWriterMock).setException(String.valueOf(runtimeExceptionMock));
    }

//SetCantReplayResponse tests

    @Test
    public void setCantReplayResponse() {
        replayProcessor.setCantReplayResponse();
        verify(replayResponseWriterMock)
                .setResultCodeAndResultMessage(ResultCode.WARNING, REPLAY_PLUGIN_NOT_SET);
    }
}