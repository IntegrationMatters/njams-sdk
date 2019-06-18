package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_TRACING;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class SetTracingProcessorTest extends AbstractConfigurationProcessor {

    private SetTracingProcessor setTracingProcessor;

    @Before
    public void setNewProcessor() {
        setTracingProcessor = spy(new SetTracingProcessor(njamsMock));
    }

    @Test
    public void setTracingWithoutAnyNeededParameters() {
        instructionBuilder.prepareInstruction(SET_TRACING);
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY,
                TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void setTracingWithoutPath() {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultActivityId();
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void setTracingWithoutActivityId() {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultPath();
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void setInvalidEndTime() {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultActivityId().addDefaultPath()
                .addEndTime("Invalid");
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.END_TIME_KEY);
    }

    @Test
    public void deleteTracingBecausePredefinedEndtimeIsBeforeNow() {
        LocalDateTime endTimeToSet = DateTimeUtility.now().minusSeconds(1);
        String serializedEndTime = DateTimeUtility.toString(endTimeToSet);
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEndTime(serializedEndTime);
        doNothing().when(setTracingProcessor).deleteTracePoint(any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).deleteTracePoint(any());
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsNotSet() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath();
        doNothing().when(setTracingProcessor).deleteTracePoint(any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).deleteTracePoint(any());
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsSetWithInvalidValue() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEnableTracing("INVALID");
        doNothing().when(setTracingProcessor).deleteTracePoint(any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).deleteTracePoint(any());
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsSetToFalse() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEnableTracing(Boolean.FALSE.toString());
        doNothing().when(setTracingProcessor).deleteTracePoint(any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).deleteTracePoint(any());
    }

    @Test
    public void updateTracingWithoutPredefinedEndTime() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addDefaultEnableTracing();
        doNothing().when(setTracingProcessor).updateTracePoint(any(), any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).updateTracePoint(any(), any());
    }

    @Test
    public void updateTracingWithPredefinedEndTime() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addDefaultEndTime().
                addDefaultEnableTracing();
        doNothing().when(setTracingProcessor).updateTracePoint(any(), any());
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor).updateTracePoint(any(), any());
    }

    @Test
    public void updateTracePointWithInvalidStartTime() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultEndTime().
                addDefaultEnableTracing().
                addStartTime("INVALID");
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.START_TIME_KEY);
    }

    @Test
    public void updateTracePoint() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultEnableTracing().
                addDefaultStartTime().
                addDefaultEndTime().
                addDefaultIterations().
                addDefaultDeepTrace();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(processConfiguration);
        setTracingProcessor.processInstruction(instructionBuilder.build());
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNotNull(tracepoint);
        assertEquals(TestInstructionBuilder.START_TIME_VALUE, tracepoint.getStarttime());
        assertEquals(TestInstructionBuilder.END_TIME_VALUE, tracepoint.getEndtime());
        assertTrue(TestInstructionBuilder.ITERATIONS_VALUE == tracepoint.getIterations().intValue());
        assertTrue(TestInstructionBuilder.DEEP_TRACE_VALUE == tracepoint.isDeeptrace().booleanValue());
        verify(setTracingProcessor).saveConfiguration(any());
    }

    @Test
    public void deleteTracePointWithNoProcessConfiguration() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId();

        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor, times(0)).saveConfiguration(any());
    }
    @Test
    public void deleteTracePointWithNoActivityConfiguration() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(processConfiguration);
        setTracingProcessor.processInstruction(instructionBuilder.build());
        verify(setTracingProcessor, times(0)).saveConfiguration(any());
    }
    @Test
    public void deleteTracePoint() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        addTracePointToActivityConfig(activityConfiguration, TestInstructionBuilder.ITERATIONS_VALUE,
                TestInstructionBuilder.START_TIME_VALUE, TestInstructionBuilder.END_TIME_VALUE,
                TestInstructionBuilder.DEEP_TRACE_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(processConfiguration);
        setTracingProcessor.processInstruction(instructionBuilder.build());
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNull(tracepoint);
        verify(setTracingProcessor).saveConfiguration(any());
    }
}