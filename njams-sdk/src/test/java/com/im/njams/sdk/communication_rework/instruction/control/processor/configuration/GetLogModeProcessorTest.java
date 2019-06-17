package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.GET_LOG_MODE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GetLogModeProcessorTest extends AbstractConfigurationProcessor {

    private GetLogModeProcessor getLogModeProcessor;

    @Before
    public void setNewProcessor() {
        getLogModeProcessor = spy(new GetLogModeProcessor(njamsMock));
    }

    @Test
    public void processInstruction() {
        instructionBuilder.prepareInstruction(GET_LOG_MODE);
        Instruction instruction = instructionBuilder.build();
        final LogMode logModeSet = configuration.getLogMode();
        when(njamsMock.getLogModeFromConfiguration()).thenReturn(logModeSet);
        getLogModeProcessor.processInstruction(instruction);

        final Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());

        final Map<String, String> parameters = response.getParameters();
        assertEquals(logModeSet.name(), parameters.get(TestInstructionBuilder.LOG_MODE_KEY));
    }
}