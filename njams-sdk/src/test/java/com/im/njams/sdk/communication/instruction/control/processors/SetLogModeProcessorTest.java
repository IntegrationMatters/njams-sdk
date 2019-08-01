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
package com.im.njams.sdk.communication.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_LOG_MODE;
import static com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction.UNABLE_TO_DESERIALZE_OBJECT;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class SetLogModeProcessorTest extends AbstractConfigurationProcessorHelper {

    private SetLogModeProcessor setLogModeProcessor;

    @Before
    public void setNewProcessor() {
        setLogModeProcessor = spy(new SetLogModeProcessor(njamsMock));
    }

    @Test
    public void setLogModeWithoutLogMode() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_MODE);
        checkResultMessageForMissingsParameters(setLogModeProcessor, TestInstructionBuilder.LOG_MODE_KEY);
    }

    @Test
    public void setLogModeWithInvalidLogMode() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_MODE).addLogMode("INVALID");
        checkResultMessageForMissingsParameters(setLogModeProcessor, UNABLE_TO_DESERIALZE_OBJECT);
    }

    @Test
    public void setLogMode() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_MODE).addDefaultLogMode();
        Instruction instruction = instructionBuilder.build();

        setLogModeProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        verify(njamsMock).setLogModeToConfiguration(TestInstructionBuilder.LOG_MODE_VALUE);
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }
}