/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.communication;

import com.im.njams.sdk.api.communication.instruction.InstructionListener;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommunicationFacadeTest {

    private CommunicationFacade communicationFacade;

    private InstructionListener instructionListenerMock;

    @Before
    public void initialize(){
        instructionListenerMock = mock(InstructionListener.class);
        communicationFacade = spy(new CommunicationFacade());
    }

//Set and GetInstructionListener tests

    @Test
    public void gettingWithoutSettingReturnsNull(){
        assertNull(communicationFacade.getInstructionListener());
    }

    @Test
    public void setAndGetInstructionListenerAreEquals(){
        communicationFacade.setInstructionListener(instructionListenerMock);
        assertNotNull(communicationFacade.getInstructionListener());
        assertEquals(instructionListenerMock, communicationFacade.getInstructionListener());
    }

    @Test
    public void setInstructionListenerOverwritesOldInstructionListener(){
        InstructionListener overwriteableInstructionListener = mock(InstructionListener.class);
        communicationFacade.setInstructionListener(overwriteableInstructionListener);
        communicationFacade.setInstructionListener(instructionListenerMock);
        assertEquals(instructionListenerMock, communicationFacade.getInstructionListener());
    }

//Stop tests

    @Test
    public void closeIsCalledOnInstructionListenerAfterStop() throws Exception {
        communicationFacade.setInstructionListener(instructionListenerMock);
        communicationFacade.stop();
        verify(instructionListenerMock).close();
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void instructionListenerThrowsExceptionWhileClosing() throws Exception {
        communicationFacade.setInstructionListener(instructionListenerMock);
        doThrow(new Exception("Test")).when(instructionListenerMock).close();
        communicationFacade.stop();
    }
}