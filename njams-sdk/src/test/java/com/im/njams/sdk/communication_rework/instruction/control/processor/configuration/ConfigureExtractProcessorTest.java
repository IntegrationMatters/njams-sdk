package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.serializer.JsonSerializer;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.CONFIGURE_EXTRACT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfigureExtractProcessorTest extends AbstractConfigurationProcessor {

    private ConfigureExtractProcessor configureExtractProcessor;

    @Before
    public void setNewProcessor(){
        configureExtractProcessor = spy(new ConfigureExtractProcessor(njamsMock));
    }

    @Test
    public void configureExtractWithoutAnyNeededParameters(){
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT);
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.ACTIVITYID_KEY, TestInstructionBuilder.EXTRACT_KEY);
    }

    @Test
    public void configureExtractWithoutPathAndExtract() {
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultActivityId();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.EXTRACT_KEY);
    }
    @Test
    public void configureExtractWithoutPathAndActivityId() throws JsonProcessingException {
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultExtract();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.ACTIVITYID_KEY);
    }
    @Test
    public void configureExtractWithoutExtractAndActivityId(){
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultPath();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.ACTIVITYID_KEY, TestInstructionBuilder.EXTRACT_KEY);
    }
    @Test
    public void configureExtractWithoutPath() throws JsonProcessingException {
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultActivityId().addDefaultExtract();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }
    @Test
    public void configureExtractWithoutActivity() throws JsonProcessingException {
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultPath().addDefaultExtract();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void configureExtractWithoutExtract(){
        instructionBuilder.prepareInstruction(CONFIGURE_EXTRACT).addDefaultPath().addDefaultActivityId();
        checkResultMessageForMissingsParameters(configureExtractProcessor, TestInstructionBuilder.EXTRACT_KEY);
    }

    @Test
    public void configureExtractWithoutExistingConfiguration() throws Exception {
        instructionBuilder.
                prepareInstruction(CONFIGURE_EXTRACT).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultExtract();
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        Map<String, ProcessConfiguration> processes = configuration.getProcesses();
        when(njamsMock.getProcessesFromConfiguration()).thenReturn(processes);

        configureExtractProcessor.processInstruction(instruction);

        verify(configureExtractProcessor).saveConfiguration(any());

        ProcessConfiguration newlyCreatedProcess = processes.get(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration newlyCreatedActivity = newlyCreatedProcess.getActivity(TestInstructionBuilder.ACTIVITYID_VALUE);
        JsonSerializer<Extract> serializer = new JsonSerializer();
        assertEquals(instruction.getRequest().getParameters().get(TestInstructionBuilder.EXTRACT_KEY), serializer.serialize(newlyCreatedActivity.getExtract()));

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());

    }

    @Test
    public void configureExtractWithConfiguration() throws Exception {
        instructionBuilder.
                prepareInstruction(CONFIGURE_EXTRACT).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultExtract();
        Instruction instruction = instructionBuilder.build();

        ProcessConfiguration process = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(process, TestInstructionBuilder.ACTIVITYID_VALUE);
        String extractName = "TEST";
        Extract extract = setExtractToActivityConfig(activityConfiguration, extractName);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        configureExtractProcessor.processInstruction(instruction);

        verify(configureExtractProcessor).saveConfiguration(any());

        Map<String, ProcessConfiguration> processes = configuration.getProcesses();
        ProcessConfiguration returnedProcess = processes.get(TestInstructionBuilder.PROCESSPATH_VALUE);
        assertEquals(process, returnedProcess);
        ActivityConfiguration returnedActivity = returnedProcess.getActivity(TestInstructionBuilder.ACTIVITYID_VALUE);
        assertEquals(activityConfiguration, returnedActivity);

        Extract returnedExtract = activityConfiguration.getExtract();
        assertNotEquals(extract, returnedExtract);
        JsonSerializer<Extract> serializer = new JsonSerializer();
        assertEquals(instruction.getRequest().getParameters().get(TestInstructionBuilder.EXTRACT_KEY), serializer.serialize(returnedExtract));

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }
}