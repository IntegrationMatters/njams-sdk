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

package com.im.njams.sdk.communication.receiver;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.communication.receiver.instruction.boundary.InstructionProcessorService;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.InstructionProcessor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsReceiverTest {

    private NjamsReceiver njamsReceiver;

    private Instruction instruction;

    private com.faizsiegeln.njams.messageformat.v4.command.Instruction messageFormatInstruction;

    private Request request;

    private Response response;


    @Before
    public void initialize() {
        njamsReceiver = spy(new NjamsReceiver());
        messageFormatInstruction = spy(new com.faizsiegeln.njams.messageformat.v4.command.Instruction());
        request = spy(new Request());
        request.setCommand(InstructionProcessorImpl.TEST_COMMAND);
        response = spy(new Response());
        messageFormatInstruction.setRequest(request);
        messageFormatInstruction.setResponse(response);
        instruction = spy(new NjamsInstruction(messageFormatInstruction));
    }

//OnInstruction tests

    @Test
    public void onInstructionWithoutAnyInstructionProcessorAddedIsHandledNevertheless() {
        njamsReceiver.onInstruction(instruction);
        assertFalse(instruction.getResponseWriter().isEmpty());
    }

    @Test
    public void onInstructionWithCorrespondingInstructionProcessor() {
        InstructionProcessorService instructionProcessorService = njamsReceiver.getInstructionProcessorService();
        instructionProcessorService.addInstructionProcessor(new InstructionProcessorImpl());

        njamsReceiver.onInstruction(instruction);
        assertEquals(InstructionProcessorImpl.TEST_COMMAND, response.getResultMessage());
    }

//GetInstructionProcessorService tests

    @Test
    public void getInstructionProcessorServiceIsNotNullAfterInitializingTheReceiver() {
        assertNotNull(njamsReceiver.getInstructionProcessorService());
    }

//Close tests

    @Test
    public void closeRemovesAllInstructionProcessorsFromInstructionProcessorService() {
        InstructionProcessorService instructionProcessorService = njamsReceiver.getInstructionProcessorService();
        instructionProcessorService.addInstructionProcessor(new InstructionProcessorImpl());

        assertNotNull(instructionProcessorService.getInstructionProcessor(InstructionProcessorImpl.TEST_COMMAND));
        njamsReceiver.close();
        assertNull(instructionProcessorService.getInstructionProcessor(InstructionProcessorImpl.TEST_COMMAND));
    }

    @Test
    public void afterCloseFallbackProcessingStillWorks() {
        njamsReceiver.close();
        njamsReceiver.onInstruction(instruction);
        assertFalse(instruction.getResponseWriter().isEmpty());
    }

    private static class InstructionProcessorImpl implements InstructionProcessor {

        private static final String TEST_COMMAND = "Test";

        @Override
        public String getCommandToListenTo() {
            return TEST_COMMAND;
        }

        @Override
        public void processInstruction(Instruction instructionToProcess) {
            instructionToProcess.getResponseWriter().setResultMessage(TEST_COMMAND);
        }
    }
}