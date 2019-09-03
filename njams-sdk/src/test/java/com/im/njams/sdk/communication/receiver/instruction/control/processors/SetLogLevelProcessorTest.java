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
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_LOG_LEVEL;
import static com.im.njams.sdk.adapter.messageformat.command.entity.NjamsInstruction.UNABLE_TO_DESERIALIZE_OBJECT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SetLogLevelProcessorTest extends AbstractConfigurationProcessorHelper {

    private SetLogLevelProcessor setLogLevelProcessor;

    @Before
    public void setNewProcessor() {
        setLogLevelProcessor = spy(new SetLogLevelProcessor(njamsMock));
    }

    @Test
    public void setLogLevelWithoutAnyNeededParameters() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL);
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.PROCESSPATH_KEY,
                TestInstructionBuilder.LOG_LEVEL_KEY);
    }

    @Test
    public void setLogLevelWithoutPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultLogLevel();
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void setLogLevelWithoutLogLevel() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath();
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.LOG_LEVEL_KEY);
    }

    @Test
    public void setLogLevelWithInvalidLogLevel() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath().addLogLevel("INVALID");
        checkResultMessageForMissingsParameters(setLogLevelProcessor, UNABLE_TO_DESERIALIZE_OBJECT);
    }

    @Test
    public void setLogLevelWithoutExistingConfiguration() throws NjamsInstructionException {
        testSetLogLevel();
    }

    @Test
    public void setSetLogLevelWithExistingConfiguration() throws NjamsInstructionException {
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        final boolean excludedToOverride = !Boolean.valueOf(TestInstructionBuilder.EXCLUDED_VALUE);
        final LogLevel logLevelToOverride = LogLevel.WARNING;

        processConfiguration.setExclude(excludedToOverride);
        processConfiguration.setLogLevel(logLevelToOverride);

        testSetLogLevel();

        assertNotEquals(processConfiguration.isExclude(), excludedToOverride);
        assertNotEquals(processConfiguration.getLogLevel(), logLevelToOverride);
    }

    private void testSetLogLevel() throws NjamsInstructionException {
        final LogMode logModeToSet = LogMode.EXCLUSIVE;
        final LogLevel logLevelToSet = LogLevel.ERROR;

        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath().addLogLevel(logLevelToSet.name())
                .addLogMode(logModeToSet.name()).addDefaultExcluded();
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        Map<String, ProcessConfiguration> processes = configuration.getProcesses();
        when(njamsMock.getProcessesFromConfiguration()).thenReturn(processes);

        setLogLevelProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        ProcessConfiguration newlyCreatedProcess = processes.get(TestInstructionBuilder.PROCESSPATH_VALUE);

        verify(njamsMock).setLogModeToConfiguration(logModeToSet);
        assertEquals(logLevelToSet, newlyCreatedProcess.getLogLevel());
        assertEquals(Boolean.valueOf(TestInstructionBuilder.EXCLUDED_VALUE), newlyCreatedProcess.isExclude());

        verify(njamsMock).saveConfigurationFromMemoryToStorage();

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }
}