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

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Tracepoint;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_TRACING;
import static com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultInstruction.UNABLE_TO_DESERIALZE_OBJECT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SetTracingProcessorTest extends AbstractConfigurationProcessorHelper {

    private SetTracingProcessor setTracingProcessor;

    @Before
    public void setNewProcessor() {
        setTracingProcessor = spy(new SetTracingProcessor(njamsMock));
    }

    @Test
    public void setTracingWithoutAnyNeededParameters() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_TRACING);
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY,
                TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void setTracingWithoutPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultActivityId();
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void setTracingWithoutActivityId() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultPath();
        checkResultMessageForMissingsParameters(setTracingProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void setInvalidEndTime() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(SET_TRACING).addDefaultActivityId().addDefaultPath()
                .addEndTime("Invalid");
        checkResultMessageForMissingsParameters(setTracingProcessor, UNABLE_TO_DESERIALZE_OBJECT);
    }

    @Test
    public void deleteTracingBecausePredefinedEndtimeIsBeforeNow() throws NjamsInstructionException {
        LocalDateTime endTimeToSet = DateTimeUtility.now().minusSeconds(1);
        String serializedEndTime = DateTimeUtility.toString(endTimeToSet);
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEndTime(serializedEndTime);
        doNothing().when(setTracingProcessor).deleteTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).deleteTracePoint();
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsNotSet() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath();
        doNothing().when(setTracingProcessor).deleteTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).deleteTracePoint();
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsSetWithInvalidValue() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEnableTracing("INVALID");
        doNothing().when(setTracingProcessor).deleteTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).deleteTracePoint();
    }

    @Test
    public void deleteTracingBecauseEnableTracingIsSetToFalse() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addEnableTracing(Boolean.FALSE.toString());
        doNothing().when(setTracingProcessor).deleteTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).deleteTracePoint();
    }

    @Test
    public void updateTracingWithoutPredefinedEndTime() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addDefaultEnableTracing();
        doNothing().when(setTracingProcessor).updateTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).updateTracePoint();
    }

    @Test
    public void updateTracingWithPredefinedEndTime() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultActivityId().
                addDefaultPath().
                addDefaultEndTime().
                addDefaultEnableTracing();
        doNothing().when(setTracingProcessor).updateTracePoint();
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(setTracingProcessor).updateTracePoint();
    }

    @Test
    public void updateTracePointWithInvalidStartTime() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId().
                addDefaultEndTime().
                addDefaultEnableTracing().
                addStartTime("INVALID");
        checkResultMessageForMissingsParameters(setTracingProcessor, UNABLE_TO_DESERIALZE_OBJECT);
    }

    @Test
    public void updateTracePointWithPredefinedStartAndEndTime() throws NjamsInstructionException {
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
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNotNull(tracepoint);
        assertEquals(TestInstructionBuilder.START_TIME_VALUE, tracepoint.getStarttime());
        assertEquals(TestInstructionBuilder.END_TIME_VALUE, tracepoint.getEndtime());
        assertTrue(TestInstructionBuilder.ITERATIONS_VALUE == tracepoint.getIterations().intValue());
        assertTrue(TestInstructionBuilder.DEEP_TRACE_VALUE == tracepoint.isDeeptrace().booleanValue());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void updateTracePointWithoutStartTime() throws NjamsInstructionException {
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
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNotNull(tracepoint);
        assertNotNull(tracepoint.getStarttime());
        assertEquals(TestInstructionBuilder.END_TIME_VALUE, tracepoint.getEndtime());
        assertTrue(TestInstructionBuilder.ITERATIONS_VALUE == tracepoint.getIterations().intValue());
        assertTrue(TestInstructionBuilder.DEEP_TRACE_VALUE == tracepoint.isDeeptrace().booleanValue());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void updateTracePointWithEndTimeBeforeStartTime() throws NjamsInstructionException {
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
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNotNull(tracepoint);
        assertEquals(startInTenMinutes, tracepoint.getStarttime());
        assertEquals(endInFiveMinutes, tracepoint.getEndtime());
        //Todo: This shouldn't be possible, it is an illegal state.
        assertTrue(startInTenMinutes.isAfter(endInFiveMinutes));
        assertTrue(TestInstructionBuilder.ITERATIONS_VALUE == tracepoint.getIterations().intValue());
        assertTrue(TestInstructionBuilder.DEEP_TRACE_VALUE == tracepoint.isDeeptrace().booleanValue());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void deleteTracePointWithNoProcessConfiguration() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId();

        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(njamsMock, times(0)).saveConfigurationFromMemoryToStorage();
    }
    @Test
    public void deleteTracePointWithNoActivityConfiguration() throws NjamsInstructionException {
        instructionBuilder.
                prepareInstruction(SET_TRACING).
                addDefaultPath().
                addDefaultActivityId();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(processConfiguration);
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        verify(njamsMock, times(0)).saveConfigurationFromMemoryToStorage();
    }
    @Test
    public void deleteTracePoint() throws NjamsInstructionException {
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
        setTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instructionBuilder.build()));
        Tracepoint tracepoint = activityConfiguration.getTracepoint();
        assertNull(tracepoint);
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }
}