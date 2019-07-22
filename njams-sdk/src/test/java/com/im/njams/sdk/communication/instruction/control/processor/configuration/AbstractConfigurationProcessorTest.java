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
package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class AbstractConfigurationProcessorTest {

    private Njams njamsMock;

    private AbstractConfigurationProcessor processor;

    @Before
    public void setNewNjamsMockAndProcessor(){
        njamsMock = mock(Njams.class);
        processor = spy(new ConfigurationProcessorImpl(njamsMock));
    }

    @Test
    public void processInstructionCreatesInstructionSupportAndCallsProcessInstructionRespectively() {
        Instruction instruction = new Instruction();
        processor.processInstruction(instruction);
        verify(processor).processInstruction((AbstractConfigurationProcessor.InstructionSupport) any());
    }

    @Test
    public void saveConfiguration() {
        processor.saveConfiguration(any());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void saveConfigurationFailed(){
        NjamsSdkRuntimeException exceptionToThrow = new NjamsSdkRuntimeException("Test");
        doThrow(exceptionToThrow).when(njamsMock).saveConfigurationFromMemoryToStorage();
        Instruction instructionToCheck = new Instruction();
        instructionToCheck.setRequest(new Request());
        AbstractConfigurationProcessor.InstructionSupport instructionSupport = spy(new AbstractConfigurationProcessor.InstructionSupport(instructionToCheck));
        processor.saveConfiguration(instructionSupport);
        verify(instructionSupport).error(anyString(), any());

        Response response = instructionToCheck.getResponse();
        assertEquals(1, response.getResultCode());
        assertEquals(AbstractConfigurationProcessor.UNABLE_TO_SAVE_CONFIGURATION + ": " + exceptionToThrow.getMessage(), response.getResultMessage());
        assertEquals(null, response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }

    @Test
    public void dontOverwriteAlreadySetResponseByDefault_SDK_148() {

        Instruction instruction = new Instruction();
        Response response = new Response();

        final LocalDateTime time = LocalDateTime.of(2010, 12, 31, 1, 0);
        response.setDateTime(time);

        final int resultCode = 4711;
        response.setResultCode(resultCode);

        final String resultMessage = "XXX";
        response.setResultMessage(resultMessage);

        instruction.setResponse(response);

        processor.processInstruction(instruction);

        verify(processor, never()).saveConfiguration(any());

        Response responseAfterProcessing = instruction.getResponse();

        assertEquals(response, responseAfterProcessing);
        assertEquals(resultCode, responseAfterProcessing.getResultCode());
        assertEquals(resultMessage, responseAfterProcessing.getResultMessage());
        assertEquals(time, responseAfterProcessing.getDateTime());
    }

    private class ConfigurationProcessorImpl extends AbstractConfigurationProcessor {

        public ConfigurationProcessorImpl(Njams njams) {
            super(njams);
        }

        @Override
        protected void processInstruction(InstructionSupport instructionSupport) {
            //Do nothing
        }
    }
}