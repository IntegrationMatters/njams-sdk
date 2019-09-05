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

package com.im.njams.sdk.communication.receiver.instruction.control.templates;

import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.replay.NjamsReplayResponseWriter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ReplayProcessorTemplateTest {

    private ReplayProcessorTemplate replayProcessorTemplate;

    private NjamsReplayInstruction replayInstructionMock;

    private NjamsReplayRequestReader replayRequestReaderMock;

    private NjamsReplayResponseWriter replayResponseWriterMock;

    private RuntimeException runtimeExceptionMock;

    @Before
    public void initialize() {
        replayProcessorTemplate = spy(new ReplayProcessorTemplateImpl());
        replayInstructionMock = mock(NjamsReplayInstruction.class);
        replayRequestReaderMock = mock(NjamsReplayRequestReader.class);
        replayResponseWriterMock = mock(NjamsReplayResponseWriter.class);
        runtimeExceptionMock = mock(RuntimeException.class);
        doReturn(replayInstructionMock).when(replayProcessorTemplate).getInstruction();
        when(replayInstructionMock.getRequestReader()).thenReturn(replayRequestReaderMock);
        when(replayInstructionMock.getResponseWriter()).thenReturn(replayResponseWriterMock);
    }

//Process tests

    private static final String CANT_REPLAY = "cs";
    private static final String REPLAY_SUCCESS = "Cs";
    private static final String REPLAY_EXCEPTION = "CS";

    @Test
    public void processCantReplay() {
        process(CANT_REPLAY);
    }

    @Test
    public void processReplaysSuccessfully() {
        process(REPLAY_SUCCESS);
    }

    @Test
    public void processThrowsExceptionWhileReplaying() {
        process(REPLAY_EXCEPTION);
    }

    private void process(String transitionWord) {
        mockProcessMethods(transitionWord);

        replayProcessorTemplate.process();

        verifyCorrectProcessing(transitionWord);
    }

    private void mockProcessMethods(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        doReturn(parseCharToBoolean(trueOrFalseChars[0])).when(replayProcessorTemplate).canReplay();
        if (parseCharToBoolean(trueOrFalseChars[1])) {
            doThrow(runtimeExceptionMock).when(replayProcessorTemplate).processReplayInstruction();
        }
    }

    private boolean parseCharToBoolean(char booleanCharacter) {
        if (Character.isLowerCase(booleanCharacter)) {
            return false;
        } else {
            return true;
        }
    }

    private void verifyCorrectProcessing(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        verify(replayProcessorTemplate).canReplay();

        if (parseCharToBoolean(trueOrFalseChars[0])) {
            verify(replayProcessorTemplate).processReplayInstruction();
            if (parseCharToBoolean(trueOrFalseChars[1])) {
                verify(replayProcessorTemplate).setExceptionResponse(runtimeExceptionMock);
            }
            verify(replayProcessorTemplate, times(0)).setCantReplayResponse();
        } else {
            verify(replayProcessorTemplate, times(0)).processReplayInstruction();
            verify(replayProcessorTemplate, times(0)).setExceptionResponse(runtimeExceptionMock);
            verify(replayProcessorTemplate).setCantReplayResponse();
        }
    }

//GetReplayRequestReader tests

    @Test
    public void getReplayRequestReaderReturnsARequestReaderTypeObject() {
        assertTrue(replayProcessorTemplate.getRequestReader() instanceof NjamsReplayRequestReader);
    }

//GetReplayResponseWriter tests

    @Test
    public void getReplayResponseWriterReturnsAResponseWriterTypeObject() {
        assertTrue(replayProcessorTemplate.getResponseWriter() instanceof NjamsReplayResponseWriter);
    }

//Private helper classes

    private class ReplayProcessorTemplateImpl extends ReplayProcessorTemplate {

        @Override
        protected boolean canReplay() {
            //Do nothing
            return false;
        }

        @Override
        protected void processReplayInstruction() {
            //Do nothing
        }

        @Override
        protected void setExceptionResponse(RuntimeException ex) {
            //Do nothing
        }

        @Override
        protected void setCantReplayResponse() {
            //Do nothing
        }

        @Override
        public String getCommandToListenTo() {
            return null;
        }
    }
}