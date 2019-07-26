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

package com.im.njams.sdk.communication.instruction.control.processor.templates;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.RequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class InstructionProcessorTemplateTest {

    private InstructionProcessorTemplate instructionProcessorTemplate;

    private Instruction instructionMock;

    private RequestReader requestReaderMock;

    @Before
    public void initialize(){
        instructionProcessorTemplate = spy(new InstructionProcessorTemplateImpl());
        instructionMock = mock(InstructionImpl.class);
        requestReaderMock = mock(RequestReader.class);
        when(instructionMock.getRequestReader()).thenReturn(requestReaderMock);
        when(requestReaderMock.getCommand()).thenReturn(Command.TEST_EXPRESSION.commandString());
    }

//ProcessInstruction tests

    @Test
    public void processInstructionCallsProcess(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        verify(instructionProcessorTemplate).process();
    }

    @Test
    public void processInstructionLogsProcessedCommand(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        verify(instructionProcessorTemplate).logProcessing();
        verify(instructionMock).getRequestReader();
        verify(requestReaderMock).getCommand();
    }

    @Test
    public void processInstructionSetsTheInstruction(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertEquals(instructionMock, instructionProcessorTemplate.getInstruction());
    }

//getInstruction tests

    @Test
    public void getInstructionReturnsAnInstructionImplTypeObject(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertTrue(instructionProcessorTemplate.getInstruction() instanceof InstructionImpl);
    }

//Private helper classes

    private class InstructionProcessorTemplateImpl extends InstructionProcessorTemplate<InstructionImpl>{

        @Override
        protected void process() {
            //Do nothing
        }
    }

    private class InstructionImpl implements Instruction{

        @Override
        public RequestReader getRequestReader() {
            //Do nothing
            return null;
        }

        @Override
        public ResponseWriter getResponseWriter() {
            //Do nothing
            return null;
        }
    }
}