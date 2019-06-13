package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_LEVEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GetLogLevelProcessorTest extends AbstractConfigurationProcessor {

    private GetLogLevelProcessor getLogLevelProcessor;

    @Before
    public void setNewProcessor(){
        getLogLevelProcessor = spy(new GetLogLevelProcessor(njamsMock));
    }

    @Test
    public void getLogLevelWithoutExistingConfiguration(){
        instructionBuilder.prepareInstruction(Command.GET_LOG_LEVEL).addPath(TestInstructionBuilder.TESTPATH);
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.TESTPATH)).thenReturn(READ_ONLY_CONFIGURATION.getProcess(TestInstructionBuilder.TESTPATH));
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(READ_ONLY_CONFIGURATION.getLogMode());
        getLogLevelProcessor.processInstruction(instruction);
        final Response response = instruction.getResponse();

        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        Map<String, String> parameters = response.getParameters();
        assertEquals("INFO", parameters.get("logLevel"));
        assertEquals("COMPLETE", parameters.get("logMode"));
        assertEquals("false", parameters.get("exclude"));
    }

    @Test
    public void getLogLevelWithExistingConfiguration(){
        ProcessConfiguration process = addProcessConfig(TestInstructionBuilder.TESTPATH);
        process.setExclude(true);
        process.setLogLevel(LogLevel.WARNING);
        writeableConfiguration.setLogMode(LogMode.EXCLUSIVE);

        instructionBuilder.prepareInstruction(Command.GET_LOG_LEVEL).addPath(TestInstructionBuilder.TESTPATH);
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.TESTPATH)).thenReturn(writeableConfiguration.getProcess(TestInstructionBuilder.TESTPATH));
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(writeableConfiguration.getLogMode());
        getLogLevelProcessor.processInstruction(instruction);
        final Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());

        final Map<String, String> parameters = response.getParameters();
        assertEquals("WARNING", parameters.get("logLevel"));
        assertEquals("EXCLUSIVE", parameters.get("logMode"));
        assertEquals("true", parameters.get("exclude"));
    }

    @Test
    public void getLogLevelWithoutPath(){
            instructionBuilder.prepareInstruction(GET_LOG_LEVEL);
        Instruction instruction = instructionBuilder.build();
        getLogLevelProcessor.processInstruction(instruction);
            Response response = instruction.getResponse();
            assertEquals(1, response.getResultCode());
            assertTrue(response.getResultMessage().contains("processPath"));

            Map<String, String> parameters = response.getParameters();
            assertTrue(parameters.isEmpty());

    }
}