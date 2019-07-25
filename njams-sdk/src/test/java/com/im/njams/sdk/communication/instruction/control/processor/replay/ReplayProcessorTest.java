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
package com.im.njams.sdk.communication.instruction.control.processor.replay;

import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReplayProcessorTest {

    private ReplayProcessor replayProcessor;

    private ReplayPlugin replayPluginMock = mock(ReplayPlugin.class);

    private ReplayHandler replayHandlerMock = mock(ReplayHandler.class);

    private RuntimeException runtimeExceptionMock = mock(RuntimeException.class);

    @Before
    public void initialize() {
        replayProcessor = spy(new ReplayProcessor(replayPluginMock));
        when(replayPluginMock.getPluginItem()).thenReturn(replayHandlerMock);
    }

//AfterInit

    @Test
    public void afterInit() {
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
        assertEquals(null, replayProcessor.caughtExceptionWhileReplaying);
    }

//PrepareProcessing tests

    @Test
    public void doesPrepareProcessingClearTheReplayStatus() {
        ReplayProcessor.ReplayResponseStatus status = replayProcessor.replayStatus =
                ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS;
        replayProcessor.prepareProcessing();
        assertNotEquals(status, replayProcessor.replayStatus);
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
    }

    @Test
    public void doesPrepareProcessingClearTheException() {
        Exception exception = replayProcessor.caughtExceptionWhileReplaying = runtimeExceptionMock;
        replayProcessor.prepareProcessing();
        assertNotEquals(exception, replayProcessor.caughtExceptionWhileReplaying);
        assertNull(replayProcessor.caughtExceptionWhileReplaying);
    }

    @Test
    public void doesPrepareProcessingDoNothingToReplayHandler() {
        replayProcessor.prepareProcessing();
        verifyZeroInteractions(replayPluginMock);
    }

//Process tests

    @Test
    public void setStatusToReplayHandlerNotSetIfReplayHandlerIsNotSet() {
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(false);
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
    }

    @Test
    public void processAndReplay() {
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(true);
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS, replayProcessor.replayStatus);
    }

    @Test
    public void processAndThrowExceptionWhileReplaying() {
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(true);
        doThrow(runtimeExceptionMock).when(replayHandlerMock).replay(any(Instruction.class));
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.EXCEPTION_WAS_THROWN_WHILE_REPLAYING,
                replayProcessor.replayStatus);
        assertEquals(runtimeExceptionMock, replayProcessor.caughtExceptionWhileReplaying);
    }

//SetInstructionResponse tests

    @Test
    public void doNothingOnReplaySuccessBecauseItHasBeenSetByReplayCaller() {
        ReplayInstruction replayInstructionMock = mock(ReplayInstruction.class);
        ReplayResponseWriter responseWriterMock = mock(ReplayResponseWriter.class);
        doReturn(replayInstructionMock).when(replayProcessor).getInstruction();
        when(replayInstructionMock.getResponseWriter()).thenReturn(responseWriterMock);

        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS;
        replayProcessor.setInstructionResponse();
        verifyZeroInteractions(responseWriterMock);
    }

    @Test
    public void setInstructionResponseOnNoReplayHandlerFound() {
        ReplayInstruction replayInstructionMock = mock(ReplayInstruction.class);
        ReplayResponseWriter responseWriterMock = mock(ReplayResponseWriter.class);
        doReturn(replayInstructionMock).when(replayProcessor).getInstruction();
        when(replayInstructionMock.getResponseWriter()).thenReturn(responseWriterMock);
        mockReplayResponseWriter(responseWriterMock);

        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET;
        replayProcessor.setInstructionResponse();
        verify(responseWriterMock).setResultCode(ResponseWriter.ResultCode.WARNING);
        verify(responseWriterMock).setResultMessage(
                ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET.getMessage());
    }

    private void mockReplayResponseWriter(ReplayResponseWriter responseWriterMock){
        when(responseWriterMock.setResultCode(any())).thenReturn(responseWriterMock);
        when(responseWriterMock.setResultMessage(any())).thenReturn(responseWriterMock);
        when(responseWriterMock.setException(any())).thenReturn(responseWriterMock);
    }

    @Test
    public void setInstructionResponseOnExceptionThrownWhileReplay() {
        ReplayInstruction replayInstructionMock = mock(ReplayInstruction.class);
        ReplayResponseWriter responseWriterMock = mock(ReplayResponseWriter.class);
        doReturn(replayInstructionMock).when(replayProcessor).getInstruction();
        when(replayInstructionMock.getResponseWriter()).thenReturn(responseWriterMock);
        mockReplayResponseWriter(responseWriterMock);

        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.EXCEPTION_WAS_THROWN_WHILE_REPLAYING;
        replayProcessor.caughtExceptionWhileReplaying = runtimeExceptionMock;
        final String exceptionMessage = "ExceptionMessage";
        when(runtimeExceptionMock.getMessage()).thenReturn(exceptionMessage);
        replayProcessor.setInstructionResponse();

        final String resultMessage = replayProcessor.replayStatus.getMessage() + exceptionMessage;
        final String errorMessage = String.valueOf(runtimeExceptionMock);
        verify(responseWriterMock).setResultCode(ResponseWriter.ResultCode.ERROR);
        verify(responseWriterMock).setResultMessage(resultMessage);
        verify(responseWriterMock).setException(errorMessage);
    }
}