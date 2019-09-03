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
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_MODE;
import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_TRACING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

public class GetTracingProcessorTest extends AbstractConfigurationProcessorHelper {

    private GetTracingProcessor getTracingProcessor;

    @Before
    public void setNewProcessor() {
        getTracingProcessor = spy(new GetTracingProcessor(njamsMock));
    }

    @Test
    public void getCommandToListenToIsCorrect(){
        assertEquals(GET_TRACING.commandString(), getTracingProcessor.getCommandToListenTo());
    }

    @Test
    public void getTracingWithoutAnyNeededParameters() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING);
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY,
                TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getTracingWithoutPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultActivityId();
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void getTracingWithoutCorrectPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.PROCESSPATH_VALUE);
    }

    @Test
    public void getTracingWithoutActivityId() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath();
        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getTracingWithoutCorrectActivityId() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void getTracingWithoutSetTracePoint() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath().addDefaultActivityId();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        addActivityToProcessConfig(processConfiguration, TestInstructionBuilder.ACTIVITYID_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getTracingProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void getTracing() throws NjamsInstructionException {
        final int iterations = 5;
        final LocalDateTime start = DateTimeUtility.now();
        final LocalDateTime end = start.plusMinutes(15);
        final boolean isDeeptrace = false;
        Instruction instruction = instructionBuilder.prepareInstruction(GET_TRACING).addDefaultPath()
                .addDefaultActivityId().build();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        addTracePointToActivityConfig(activityConfiguration, iterations, start, end, isDeeptrace);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        getTracingProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        Response response = instruction.getResponse();

        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        Map<String, String> parameters = response.getParameters();
        assertEquals(DateTimeUtility.toString(start), parameters.get(TestInstructionBuilder.START_TIME_KEY));
        assertEquals(DateTimeUtility.toString(end), parameters.get(TestInstructionBuilder.END_TIME_KEY));
        assertEquals(String.valueOf(iterations), parameters.get(TestInstructionBuilder.ITERATIONS_KEY));
        assertEquals(String.valueOf(isDeeptrace), parameters.get(TestInstructionBuilder.DEEP_TRACE_KEY));

        verify(njamsMock, times(0)).saveConfigurationFromMemoryToStorage();
    }

}