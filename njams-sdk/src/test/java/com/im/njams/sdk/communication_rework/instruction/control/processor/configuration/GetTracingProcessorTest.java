package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_TRACING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GetTracingProcessorTest extends AbstractConfigurationProcessor{

    private GetTracingProcessor getTracingProcessor;

    @Before
    public void setNewProcessor(){
        getTracingProcessor = spy(new GetTracingProcessor(njamsMock));
    }

    @Test
    public void getTracingWithoutAnyNeededParameters(){
        instructionBuilder.prepareInstruction(GET_TRACING);
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getTracingWithoutPath(){
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultActivityId();
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void getTracingWithoutCorrectPath(){
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_VALUE);
    }

    @Test
    public void getTracingWithoutActivityId() {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath();
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getTracingWithoutCorrectActivityId() {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void getTracingWithoutSetTracePoint() {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        addActivityToProcessConfig(processConfiguration, TestInstructionBuilder.ACTIVITYID_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void getTracing(){
        final int iterations = 5;
        final LocalDateTime start = DateTimeUtility.now();
        final LocalDateTime end = start.plusMinutes(15);
        final boolean isDeeptrace = false;
        Instruction instruction = instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId().build();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration, TestInstructionBuilder.ACTIVITYID_VALUE);
        addTracePointToActivityConfig(activityConfiguration, iterations, start, end, isDeeptrace);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        getTracingProcessor.processInstruction(instruction);

        Response response = instruction.getResponse();

        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        Map<String, String> parameters = response.getParameters();
        assertEquals(String.valueOf(start), parameters.get("starttime"));
        assertEquals(String.valueOf(end), parameters.get("endtime"));
        assertEquals(String.valueOf(iterations), parameters.get("iterations"));
        assertEquals(String.valueOf(isDeeptrace), parameters.get("deepTrace"));
    }

}