package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.RECORD;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RecordProcessorTest extends AbstractConfigurationProcessor {

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
    public void engineWideRecording() {
        instructionBuilder.prepareInstruction(RECORD).setDefaultEngineWideRecording();
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessesFromConfiguration()).thenReturn(configuration.getProcesses());
        recordProcessor.processInstruction(instruction);

        verify(njamsMock).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertTrue(configuration.getProcesses().values().stream().allMatch(ProcessConfiguration::isRecording));
        verify(recordProcessor).saveConfiguration(any());
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
    public void afterEngineWideRecordingSetOneProcessRecordingBackToFalse() {
        instructionBuilder.prepareInstruction(RECORD).setDefaultEngineWideRecording().addDefaultPath()
                .setRecording("false");
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessesFromConfiguration()).thenReturn(configuration.getProcesses());
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        recordProcessor.processInstruction(instruction);

        verify(njamsMock).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE1).isRecording());
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE2).isRecording());
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE3).isRecording());
        verify(recordProcessor).saveConfiguration(any());
    }

    @Test
    public void setRecording() {
        instructionBuilder.prepareInstruction(RECORD).addDefaultPath().setDefaultRecording();
        Instruction instruction = instructionBuilder.build();

        fillConfigurationWithThreeProcessesWithDisabledRecording();

        assertFalse(configuration.isRecording());
        assertEquals(3, configuration.getProcesses().size());
        assertTrue(configuration.getProcesses().values().stream().noneMatch(ProcessConfiguration::isRecording));

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        recordProcessor.processInstruction(instruction);

        verify(njamsMock, times(0)).setRecordingToConfiguration(TestInstructionBuilder.ENGINE_WIDE_RECORDING_VALUE);
        assertTrue(configuration.getProcess(PROCESSPATH_VALUE1).isRecording());
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE2).isRecording());
        assertFalse(configuration.getProcess(PROCESSPATH_VALUE3).isRecording());
        verify(recordProcessor).saveConfiguration(any());
    }
}