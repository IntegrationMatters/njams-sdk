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
package com.im.njams.sdk.communication.instruction.control;

import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.communication.instruction.control.processors.test.TestInstructionProcessor;
import com.im.njams.sdk.communication.instruction.entity.InstructionProcessorCollection;
import org.junit.Before;
import org.junit.Test;

import static com.im.njams.sdk.communication.instruction.control.processors.test.TestInstructionProcessor.TEST_COMMAND;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class InstructionDispatcherTest {

    private final TestInstructionProcessor testInstructionProcessor = spy(new TestInstructionProcessor());

    private InstructionDispatcher dispatcher;

    @Before
    public void cleanUpInstructionProcessors(){
        dispatcher = new InstructionDispatcher();
        dispatcher.instructionProcessorCollection = mock(InstructionProcessorCollection.class);
    }

    @Test
    public void putInstructionProcessorUsesCollectionWithLowerCaseCommand() {
        dispatcher.putInstructionProcessor(TEST_COMMAND, testInstructionProcessor);
        verify(dispatcher.instructionProcessorCollection).putIfNotNull(TEST_COMMAND.toLowerCase(), testInstructionProcessor);
    }

    @Test
    public void getInstructionProcessorUsesCollectionWithLowerCaseCommand() {
        dispatcher.getInstructionProcessor(TEST_COMMAND);
        verify(dispatcher.instructionProcessorCollection).get(TEST_COMMAND.toLowerCase());
    }

    @Test
    public void removeInstructionProcessorUsesCollectionWithLowerCaseCommand() {
        dispatcher.removeInstructionProcessor(TEST_COMMAND);
        verify(dispatcher.instructionProcessorCollection).remove(TEST_COMMAND.toLowerCase());
    }

    @Test
    public void removeAllInstructionProcessorUsesCollection() {
        dispatcher.removeAllInstructionProcessors();
        verify(dispatcher.instructionProcessorCollection).clear();
    }

    @Test
    public void dispatch(){
        dispatcher = spy(dispatcher);
        mockProtectedMethods();
        dispatcher.dispatchInstruction(mock(Instruction.class));
        verifyProtectedMethods();
    }

    private void mockProtectedMethods(){
        doReturn(null).when(dispatcher).extractLowerCaseCommandFrom(any());
        doReturn(null).when(dispatcher).getExecutingProcessorFor(any());
        doNothing().when(dispatcher).logDispatching(any(), any());
        doNothing().when(dispatcher).processInstructionWithProcessor(any(), any());
    }

    private void verifyProtectedMethods(){
        verify(dispatcher).extractLowerCaseCommandFrom(any());
        verify(dispatcher).getExecutingProcessorFor(any());
        verify(dispatcher).logDispatching(any(), any());
        verify(dispatcher).processInstructionWithProcessor(any(), any());
    }

    @Test
    public void getDefaultProcessor(){
        when(dispatcher.instructionProcessorCollection.get(any())).thenReturn(null);
        dispatcher.getExecutingProcessorFor(TEST_COMMAND);
        verify(dispatcher.instructionProcessorCollection).getDefault();
    }

    @Test
    public void findMatchingProcessor(){
        when(dispatcher.instructionProcessorCollection.get(TEST_COMMAND)).thenReturn(testInstructionProcessor);
        InstructionProcessor foundProcessor = dispatcher.getExecutingProcessorFor(TEST_COMMAND);
        assertEquals(testInstructionProcessor, foundProcessor);
        verify(dispatcher.instructionProcessorCollection, times(0)).getDefault();
    }

    @Test
    public void processInstructionWithProcessor(){
        Instruction instructionMock = mock(Instruction.class);
        dispatcher.processInstructionWithProcessor(instructionMock, testInstructionProcessor);
        verify(testInstructionProcessor).processInstruction(instructionMock);
    }
}