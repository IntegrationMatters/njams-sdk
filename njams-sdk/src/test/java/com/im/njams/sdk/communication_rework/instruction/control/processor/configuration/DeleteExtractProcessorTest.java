package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.DELETE_EXTRACT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class DeleteExtractProcessorTest extends AbstractConfigurationProcessor{

    private DeleteExtractProcessor deleteExtractProcessor;

    @Before
    public void setNewProcessor() {
        deleteExtractProcessor = spy(new DeleteExtractProcessor(njamsMock));
    }

    @Test
    public void deleteExtractWithoutAnyNeededParameters(){
        instructionBuilder.prepareInstruction(DELETE_EXTRACT);
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void deleteExtractWithoutPath(){
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultActivityId();
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void deleteExtractWithoutCorrectPath(){
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_VALUE);
    }

    @Test
    public void deleteExtractWithoutActivityId(){
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath();
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void deleteExtractWithoutCorrectActivityId() {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void deleteExtract(){
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        Instruction instruction = instructionBuilder.build();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        Extract extract = setExtractToActivityConfig(activityConfiguration, TestInstructionBuilder.EXTRACT_KEY);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(processConfiguration);

        assertNotNull(activityConfiguration.getExtract());
        deleteExtractProcessor.processInstruction(instruction);

        assertNull(activityConfiguration.getExtract());
        verify(deleteExtractProcessor).saveConfiguration(any());
    }
}