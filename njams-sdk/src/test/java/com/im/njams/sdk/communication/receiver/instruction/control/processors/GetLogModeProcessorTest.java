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
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_LEVEL;
import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_MODE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

public class GetLogModeProcessorTest extends AbstractConfigurationProcessorHelper {

    private GetLogModeProcessor getLogModeProcessor;

    @Before
    public void setNewProcessor() {
        getLogModeProcessor = spy(new GetLogModeProcessor(njamsMock));
    }

    @Test
    public void getCommandToListenToIsCorrect(){
        assertEquals(GET_LOG_MODE.commandString(), getLogModeProcessor.getCommandToListenTo());
    }

    @Test
    public void processInstruction() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_LOG_MODE);
        Instruction instruction = instructionBuilder.build();
        final LogMode logModeSet = configuration.getLogMode();
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(logModeSet);
        getLogModeProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        final Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());

        final Map<String, String> parameters = response.getParameters();
        assertEquals(logModeSet.name(), parameters.get(TestInstructionBuilder.LOG_MODE_KEY));

        verify(njamsMock, times(0)).saveConfigurationFromMemoryToStorage();
    }
}