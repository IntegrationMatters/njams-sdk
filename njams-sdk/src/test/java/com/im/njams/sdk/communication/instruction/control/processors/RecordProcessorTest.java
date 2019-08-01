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
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.RECORD;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class RecordProcessorTest extends AbstractConfigurationProcessorHelper {

    private RecordProcessor recordProcessor;

    private static final String PROCESSPATH_VALUE1 = TestInstructionBuilder.PROCESSPATH_VALUE;
    private static final String PROCESSPATH_VALUE2 = ">test2>";
    private static final String PROCESSPATH_VALUE3 = ">test3>";

    @Before
    public void setNewProcessor() {
        recordProcessor = spy(new RecordProcessor(njamsMock));
        configuration.setRecording(false);
    }


    @Test
    public void engineWideRecording() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(RECORD).setDefaultEngineWideRecording();
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        Assert.assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessesFromConfiguration()).thenReturn(configuration.getProcesses());
        recordProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        verify(njamsMock).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertTrue(configuration.getProcesses().values().stream().allMatch(ProcessConfiguration::isRecording));
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    private void fillConfigurationWithThreeProcessesWithDisabledRecording() {
        ProcessConfiguration processConfiguration1 = addProcessConfig(PROCESSPATH_VALUE1);
        ProcessConfiguration processConfiguration2 = addProcessConfig(PROCESSPATH_VALUE2);
        ProcessConfiguration processConfiguration3 = addProcessConfig(PROCESSPATH_VALUE3);

        processConfiguration1.setRecording(false);
        processConfiguration2.setRecording(false);
        processConfiguration3.setRecording(false);
    }

    @Test
    public void afterEngineWideRecordingSetOneProcessRecordingBackToFalse() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(RECORD).setDefaultEngineWideRecording().addDefaultPath()
                .setRecording("false");
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        Assert.assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessesFromConfiguration()).thenReturn(configuration.getProcesses());
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        recordProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        verify(njamsMock).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE1).isRecording());
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE2).isRecording());
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE3).isRecording());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void setRecording() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(RECORD).addDefaultPath().setDefaultRecording();
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        Assert.assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        recordProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        verify(njamsMock, times(0)).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE1).isRecording());
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE2).isRecording());
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE3).isRecording());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }
}