/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.serializer.JsonSerializer;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_EXTRACT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GetExtractProcessorTest extends AbstractConfigurationProcessor {

    private GetExtractProcessor getExtractProcessor;

    @Before
    public void setNewProcessor() {
        getExtractProcessor = spy(new GetExtractProcessor(njamsMock));
    }

    @Test
    public void getExtractWithoutAnyNeededParameters() {
        instructionBuilder.prepareInstruction(GET_EXTRACT);
        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY,
                TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getExtractWithoutPath() {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultActivityId();
        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void getExtractWithoutCorrectPath() {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultPath().addDefaultActivityId();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.PROCESSPATH_VALUE);
    }

    @Test
    public void getExtractWithoutActivityId() {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultPath();
        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void getExtractWithoutCorrectActivity() {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultPath().addDefaultActivityId();
        addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void getExtractWithoutSetExtract() {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultPath().addDefaultActivityId();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        addActivityToProcessConfig(processConfiguration, TestInstructionBuilder.ACTIVITYID_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(getExtractProcessor, TestInstructionBuilder.EXTRACT_VALUE);
    }

    @Test
    public void getExtract() throws Exception {
        instructionBuilder.prepareInstruction(GET_EXTRACT).addDefaultPath().addDefaultActivityId();
        Instruction instruction = instructionBuilder.build();
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        Extract extract = setExtractToActivityConfig(activityConfiguration, TestInstructionBuilder.EXTRACT_KEY);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE))
                .thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        getExtractProcessor.processInstruction(instruction);

        Response response = instruction.getResponse();

        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        Map<String, String> parameters = response.getParameters();
        JsonSerializer<Extract> serializer = new JsonSerializer();
        assertEquals(serializer.serialize(extract), parameters.get(TestInstructionBuilder.EXTRACT_KEY));
    }
}