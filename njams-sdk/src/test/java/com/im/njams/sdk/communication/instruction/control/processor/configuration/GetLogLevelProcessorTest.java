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

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.communication.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_LEVEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GetLogLevelProcessorTest extends AbstractConfigurationProcessorHelper {

    private GetLogLevelProcessor getLogLevelProcessor;

    @Before
    public void setNewProcessor() {
        getLogLevelProcessor = spy(new GetLogLevelProcessor(njamsMock));
    }

    @Test
    public void getLogLevelWithoutAnyNeededParameters() {
        instructionBuilder.prepareInstruction(GET_LOG_LEVEL);
        checkResultMessageForMissingsParameters(getLogLevelProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void getLogLevelWithoutExistingProcessConfiguration() {
        instructionBuilder.prepareInstruction(Command.GET_LOG_LEVEL).addDefaultPath();
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(configuration.getLogMode());
        getLogLevelProcessor.processInstruction(instruction);
        final Response response = instruction.getResponse();

        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());

        Map<String, String> parameters = response.getParameters();
        assertEquals("INFO", parameters.get("logLevel"));
        assertEquals("COMPLETE", parameters.get("logMode"));
        assertEquals("false", parameters.get("exclude"));
    }

    @Test
    public void getLogLevelWithExistingProcessConfiguration() {
        ProcessConfiguration process = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        process.setExclude(true);
        process.setLogLevel(LogLevel.WARNING);
        configuration.setLogMode(LogMode.EXCLUSIVE);

        instructionBuilder.prepareInstruction(Command.GET_LOG_LEVEL).addDefaultPath();
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(configuration.getLogMode());
        getLogLevelProcessor.processInstruction(instruction);
        final Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());

        final Map<String, String> parameters = response.getParameters();
        assertEquals("WARNING", parameters.get(TestInstructionBuilder.LOG_LEVEL_KEY));
        assertEquals("EXCLUSIVE", parameters.get(TestInstructionBuilder.LOG_MODE_KEY));
        assertEquals(TestInstructionBuilder.EXCLUDED_VALUE, parameters.get(TestInstructionBuilder.EXCLUDED_KEY));
    }
}