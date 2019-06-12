package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class ConfigurationProcessorTest {

    private Njams njamsMock;

    private ConfigurationProcessor processor;

    @Before
    public void setNewNjamsMockAndProcessor(){
        njamsMock = mock(Njams.class);
        processor = spy(new ConfigurationProcessorImpl(njamsMock, ""));
    }

    @Test
    public void processInstructionCreatesInstructionSupportAndCallsProcessInstructionRespectively() {
        Instruction instruction = new Instruction();
        processor.processInstruction(instruction);
        verify(processor).processInstruction((ConfigurationProcessor.InstructionSupport) any());
    }

    @Test
    public void saveConfiguration() {
        processor.saveConfiguration(any());
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test
    public void saveConfigurationFailed(){
        NjamsSdkRuntimeException exceptionToThrow = new NjamsSdkRuntimeException("Test");
        doThrow(exceptionToThrow).when(njamsMock).saveConfigurationFromMemoryToStorage();
        Instruction instructionToCheck = new Instruction();
        instructionToCheck.setRequest(new Request());
        ConfigurationProcessor.InstructionSupport instructionSupport = spy(new ConfigurationProcessor.InstructionSupport(instructionToCheck));
        processor.saveConfiguration(instructionSupport);
        verify(instructionSupport).error(anyString(), any());

        Response response = instructionToCheck.getResponse();
        assertEquals(1, response.getResultCode());
        assertEquals(ConfigurationProcessor.UNABLE_TO_SAVE_CONFIGURATION + ": " + exceptionToThrow.getMessage(), response.getResultMessage());
        assertEquals(null, response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }

    private class ConfigurationProcessorImpl extends ConfigurationProcessor{

        public ConfigurationProcessorImpl(Njams njams, String commandToProcess) {
            super(njams, commandToProcess);
        }

        @Override
        protected void processInstruction(InstructionSupport instructionSupport) {
            //Do nothing
        }
    }
}