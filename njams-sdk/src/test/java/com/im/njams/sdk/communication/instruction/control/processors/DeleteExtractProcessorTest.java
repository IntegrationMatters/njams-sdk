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
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.DELETE_EXTRACT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class DeleteExtractProcessorTest extends AbstractConfigurationProcessorHelper {

    private DeleteExtractProcessor deleteExtractProcessor;

    @Before
    public void setNewProcessor() {
        deleteExtractProcessor = spy(new DeleteExtractProcessor(njamsMock));
    }

    @Test
    public void deleteExtractWithoutAnyNeededParameters() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT);
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void deleteExtractWithoutPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultActivityId();
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void deleteExtractWithoutCorrectPath() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.PROCESSPATH_VALUE);
    }

    @Test
    public void deleteExtractWithoutActivityId() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath();
        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.ACTIVITYID_KEY);
    }

    @Test
    public void deleteExtractWithoutCorrectActivityId() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        checkResultMessageForMissingsParameters(deleteExtractProcessor, TestInstructionBuilder.ACTIVITYID_VALUE);
    }

    @Test
    public void deleteExtract() throws NjamsInstructionException {
        instructionBuilder.prepareInstruction(DELETE_EXTRACT).addDefaultPath().addDefaultActivityId();
        Instruction instruction = instructionBuilder.build();

        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        ActivityConfiguration activityConfiguration = addActivityToProcessConfig(processConfiguration,
                TestInstructionBuilder.ACTIVITYID_VALUE);
        Extract extract = setExtractToActivityConfig(activityConfiguration, TestInstructionBuilder.EXTRACT_KEY);

        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(processConfiguration);

        assertNotNull(activityConfiguration.getExtract());
        deleteExtractProcessor.processInstruction((ConditionInstruction) getWrappedInstruction(instruction));

        assertNull(activityConfiguration.getExtract());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }
}