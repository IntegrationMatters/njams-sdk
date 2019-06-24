/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication_rework.instruction.control.processor.replay;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication_rework.instruction.control.processor.AbstractInstructionProcessor;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class ReplayProcessorTest extends AbstractInstructionProcessor {

    private ReplayProcessor replayProcessor;

    @Before
    public void setNewProcessor() {
        replayProcessor = spy(new ReplayProcessor());
    }

    @Test
    public void returnNoDefaultReplayHandlerAfterInit(){
        assertNull(replayProcessor.getReplayHandler());
    }

    @Test
    public void getAndSetOfReplayProcessor() {
        assertNull(replayProcessor.getReplayHandler());
        ReplayHandler replayHandler = new TestReplayHandler();
        replayProcessor.setReplayHandler(replayHandler);
        assertEquals(replayHandler, replayProcessor.getReplayHandler());
    }

    @Test
    public void processInstructionWithoutReplayHandler() {
        assertNull(replayProcessor.getReplayHandler());
        Instruction instruction = instructionBuilder.prepareInstruction(Command.REPLAY).build();
        replayProcessor.processInstruction(instruction);
        assertNull(replayProcessor.getReplayHandler());

        Response response = instruction.getResponse();
        assertEquals(1, response.getResultCode());
        assertEquals(ReplayProcessor.WARNING_RESULT_MESSAGE, response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }

    @Test
    public void fallbackBecauseReplayDidntWork(){

        ExceptionReplayHandler replayHandler = new ExceptionReplayHandler();
        replayProcessor.setReplayHandler(replayHandler);

        Instruction instruction = instructionBuilder.prepareInstruction(Command.REPLAY).build();
        replayProcessor.processInstruction(instruction);

        Response response = instruction.getResponse();
        assertEquals(2, response.getResultCode());
        assertEquals(ReplayProcessor.ERROR_RESULT_MESSAGE_PREFIX + replayHandler.exceptionMock.getMessage(), response.getResultMessage());
        assertNull(response.getDateTime());
        Map<String, String> parameters = response.getParameters();
        assertEquals(parameters.get(ReplayResponse.EXCEPTION_KEY), String.valueOf(replayHandler.exceptionMock));
    }

    @Test
    public void processInstruction(){
        TestReplayHandler replayHandler = spy(new TestReplayHandler());
        replayProcessor.setReplayHandler(replayHandler);

        Instruction instruction = instructionBuilder.prepareInstruction(Command.REPLAY).build();
        replayProcessor.processInstruction(instruction);
        verify(replayHandler).replay(any());
        verify(replayHandler.responseMock).addParametersToInstruction(instruction);

    }

    private class ExceptionReplayHandler implements ReplayHandler{

        public final NjamsSdkRuntimeException exceptionMock = spy(new NjamsSdkRuntimeException("TEST"));

        @Override
        public ReplayResponse replay(ReplayRequest request) {
            throw exceptionMock;
        }
    }

    private class TestReplayHandler implements ReplayHandler {


        public final ReplayResponse responseMock = spy(new ReplayResponse());
        @Override
        public ReplayResponse replay(ReplayRequest request) {
            return responseMock;
        }

    }
}