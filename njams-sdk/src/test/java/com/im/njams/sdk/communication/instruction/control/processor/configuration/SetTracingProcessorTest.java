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
package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_TRACING;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class SetTracingProcessorTest extends AbstractConfigurationProcessorHelper {

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
    public void updateTracePointWithPredefinedStartAndEndTime() {
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
    public void updateTracePointWithoutStartTime() {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultEnableTracing().
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
        assertNotNull(tracepoint.getStarttime());
        assertEquals(TestInstructionBuilder.END_TIME_VALUE, tracepoint.getEndtime());
        assertTrue(TestInstructionBuilder.ITERATIONS_VALUE == tracepoint.getIterations().intValue());
        assertTrue(TestInstructionBuilder.DEEP_TRACE_VALUE == tracepoint.isDeeptrace().booleanValue());
        verify(setTracingProcessor).saveConfiguration(any());
    }

    @Test
    public void updateTracePointWithEndTimeBeforeStartTime() {
        final LocalDateTime startInTenMinutes = DateTimeUtility.now().plusMinutes(10);
        final LocalDateTime endInFiveMinutes = startInTenMinutes.minusMinutes(5);

        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultEnableTracing().
                addStartTime(DateTimeUtility.toString(startInTenMinutes)).
                addEndTime(DateTimeUtility.toString(endInFiveMinutes)).
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
        assertEquals(startInTenMinutes, tracepoint.getStarttime());
        assertEquals(endInFiveMinutes, tracepoint.getEndtime());
        //Todo: This shouldn't be possible, it is an illegal state.
        assertTrue(startInTenMinutes.isAfter(endInFiveMinutes));
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