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

package com.im.njams.sdk.communication.receiver.instruction.control.processors.templates;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AbstractProcessorTemplateTest {

    private AbstractProcessorTemplate instructionProcessorTemplate;

    private Instruction instructionMock;

    private Instruction.RequestReader requestReaderMock;

    private Instruction.ResponseWriter responseWriterMock;

    @Before
    public void initialize() {
        instructionProcessorTemplate = spy(new InstructionProcessorTemplateImpl());
        instructionMock = mock(Instruction.class);
        requestReaderMock = mock(Instruction.RequestReader.class);
        responseWriterMock = mock(Instruction.ResponseWriter.class);
        when(instructionMock.getRequestReader()).thenReturn(requestReaderMock);
        when(instructionMock.getResponseWriter()).thenReturn(responseWriterMock);
        when(requestReaderMock.getCommand()).thenReturn(Command.TEST_EXPRESSION.commandString());
    }

//ProcessInstruction tests

    @Test
    public void processInstructionCallsProcess() {
        instructionProcessorTemplate.processInstruction(instructionMock);
        verify(instructionProcessorTemplate).process();
    }

    @Test
    public void processInstructionSetsTheInstruction() {
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertEquals(instructionMock, instructionProcessorTemplate.getInstruction());
    }

//getInstruction tests

    @Test
    public void getInstructionReturnsAnInstruction() {
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertTrue(instructionProcessorTemplate.getInstruction() instanceof Instruction);
    }

//getRequestReader tests

    @Test
    public void getRequestReaderTest(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertTrue(instructionProcessorTemplate.getRequestReader() instanceof Instruction.RequestReader);
    }

//getRequestReader tests

    @Test
    public void getResponseWriterTest(){
        instructionProcessorTemplate.processInstruction(instructionMock);
        assertTrue(instructionProcessorTemplate.getResponseWriter() instanceof Instruction.ResponseWriter);
    }

//Private helper classes

    private class InstructionProcessorTemplateImpl extends AbstractProcessorTemplate {

        @Override
        protected void process() {
            //Do nothing
        }

        @Override
        public String getCommandToListenTo() {
            return null;
        }
    }
}