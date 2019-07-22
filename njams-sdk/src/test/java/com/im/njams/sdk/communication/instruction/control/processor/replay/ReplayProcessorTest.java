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

import com.im.njams.sdk.api.plugin.replay.ReplayHandler;
import com.im.njams.sdk.api.plugin.replay.ReplayPlugin;
import com.im.njams.sdk.api.plugin.replay.ReplayRequest;
import com.im.njams.sdk.communication.instruction.control.processor.AbstractTestInstructionProcessor;
import com.im.njams.sdk.communication.instruction.util.InstructionWrapper;
import com.im.njams.sdk.plugin.replay.entity.NjamsReplayResponse;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReplayProcessorTest extends AbstractTestInstructionProcessor {

    private ReplayProcessor replayProcessor;

    private InstructionWrapper instructionWrapperMock = mock(InstructionWrapper.class);

    private ReplayPlugin replayPluginMock = mock(ReplayPlugin.class);

    private ReplayHandler replayHandlerMock = mock(ReplayHandler.class);

    private NjamsReplayResponse replayResponseMock = mock(NjamsReplayResponse.class);

    private RuntimeException runtimeExceptionMock = mock(RuntimeException.class);

    @Before
    public void initialize() {
        replayProcessor = spy(new ReplayProcessor(replayPluginMock));
        doReturn(instructionWrapperMock).when(replayProcessor).getInstructionWrapper();
        when(replayPluginMock.getPluginItem()).thenReturn(replayHandlerMock);
    }

//AfterInit

    @Test
    public void afterInit() {
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
        assertEquals(null, replayProcessor.replayResponse);
        assertEquals(null, replayProcessor.caughtExceptionWhileReplaying);
    }

//PrepareProcessing tests

    @Test
    public void doesPrepareProcessingClearTheReplayStatus() {
        ReplayProcessor.ReplayResponseStatus status = replayProcessor.replayStatus =
                ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS;
        replayProcessor.prepareProcessing(null);
        assertNotEquals(status, replayProcessor.replayStatus);
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
    }

    @Test
    public void doesPrepareProcessingClearTheReplayResponse() {
        NjamsReplayResponse response = replayProcessor.replayResponse = replayResponseMock;
        replayProcessor.prepareProcessing(null);
        assertNotEquals(response, replayProcessor.replayResponse);
        assertNull(replayProcessor.replayResponse);
    }

    @Test
    public void doesPrepareProcessingClearTheException() {
        Exception exception = replayProcessor.caughtExceptionWhileReplaying = runtimeExceptionMock;
        replayProcessor.prepareProcessing(null);
        assertNotEquals(exception, replayProcessor.caughtExceptionWhileReplaying);
        assertNull(replayProcessor.caughtExceptionWhileReplaying);
    }

    @Test
    public void doesPrepareProcessingDoNothingToReplayHandler() {
        replayProcessor.prepareProcessing(null);
        verifyZeroInteractions(replayPluginMock);
    }

//Process tests

    @Test
    public void setStatusToReplayHandlerNotSetIfReplayHandlerIsNotSet(){
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(false);
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET, replayProcessor.replayStatus);
    }

    @Test
    public void processAndReplay(){
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(true);
        when(replayHandlerMock.replay(any(ReplayRequest.class))).thenReturn(replayResponseMock);
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS, replayProcessor.replayStatus);
        assertEquals(replayResponseMock, replayProcessor.replayResponse);
    }

    @Test
    public void processAndThrowExceptionWhileReplaying(){
        when(replayPluginMock.isReplayHandlerSet()).thenReturn(true);
        when(replayHandlerMock.replay(any(ReplayRequest.class))).thenThrow(runtimeExceptionMock);
        replayProcessor.process();
        assertEquals(ReplayProcessor.ReplayResponseStatus.EXCEPTION_WAS_THROWN_WHILE_REPLAYING, replayProcessor.replayStatus);
        assertEquals(runtimeExceptionMock, replayProcessor.caughtExceptionWhileReplaying);
    }

//SetInstructionResponse tests

    @Test
    public void setInstructionResponseOnReplaySuccess(){
        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.REPLAY_SUCCESS;
        replayProcessor.replayResponse = replayResponseMock;
        replayProcessor.setInstructionResponse();
        verify(replayResponseMock).addSuccessParametersTo(instructionWrapperMock);
    }

    @Test
    public void setInstructionResponseOnNoReplayHandlerFound(){
        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.REPLAY_HANDLER_NOT_SET;
        replayProcessor.replayResponse = replayResponseMock;
        replayProcessor.setInstructionResponse();
        verify(replayResponseMock).addWarningparametersTo(instructionWrapperMock, replayProcessor.replayStatus.getMessage());
    }

    @Test
    public void setInstructionResponseOnExceptionThrownWhileReplay(){
        replayProcessor.replayStatus = ReplayProcessor.ReplayResponseStatus.EXCEPTION_WAS_THROWN_WHILE_REPLAYING;
        replayProcessor.replayResponse = replayResponseMock;
        replayProcessor.caughtExceptionWhileReplaying = runtimeExceptionMock;
        final String exceptionMessage = "ExceptionMessage";
        when(runtimeExceptionMock.getMessage()).thenReturn(exceptionMessage);
        replayProcessor.setInstructionResponse();

        final String resultMessage = replayProcessor.replayStatus.getMessage() + exceptionMessage;
        final String errorMessage = String.valueOf(runtimeExceptionMock);
        verify(replayResponseMock).addErrorParametersTo(instructionWrapperMock, resultMessage, errorMessage);
    }
}