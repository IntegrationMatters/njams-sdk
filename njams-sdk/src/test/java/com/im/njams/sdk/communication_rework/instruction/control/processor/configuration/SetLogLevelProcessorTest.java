package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_LOG_LEVEL;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SetLogLevelProcessorTest extends AbstractConfigurationProcessor {

    private SetLogLevelProcessor setLogLevelProcessor;

    @Before
    public void setNewProcessor() {
        setLogLevelProcessor = spy(new SetLogLevelProcessor(njamsMock));
    }

    @Test
    public void setLogLevelWithoutAnyNeededParameters() {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL);
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.PROCESSPATH_KEY, TestInstructionBuilder.LOG_LEVEL_KEY);
    }

    @Test
    public void setLogLevelWithoutPath() {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultLogLevel();
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.PROCESSPATH_KEY);
    }

    @Test
    public void setLogLevelWithoutLogLevel() {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath();
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.LOG_LEVEL_KEY);
    }

    @Test
    public void setLogLevelWithInvalidLogLevel() {
        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath().addLogLevel("INVALID");
        checkResultMessageForMissingsParameters(setLogLevelProcessor, TestInstructionBuilder.LOG_LEVEL_KEY);
    }

    @Test
    public void setLogLevelWithoutExistingConfiguration() {
        testSetLogLevel();
    }

    @Test
    public void setSetLogLevelWithExistingConfiguration() {
        ProcessConfiguration processConfiguration = addProcessConfig(TestInstructionBuilder.PROCESSPATH_VALUE);
        final boolean excludedToOverride = !TestInstructionBuilder.EXCLUDED_VALUE;
        final LogLevel logLevelToOverride = LogLevel.WARNING;

        processConfiguration.setExclude(excludedToOverride);
        processConfiguration.setLogLevel(logLevelToOverride);

        testSetLogLevel();

        assertNotEquals(processConfiguration.isExclude(), excludedToOverride);
        assertNotEquals(processConfiguration.getLogLevel(), logLevelToOverride);
    }

    private void testSetLogLevel() {
        final LogMode logModeToSet = LogMode.EXCLUSIVE;
        final LogLevel logLevelToSet = LogLevel.ERROR;

        instructionBuilder.prepareInstruction(SET_LOG_LEVEL).addDefaultPath().addLogLevel(logLevelToSet.name()).addLogMode(logModeToSet.name()).addDefaultExcluded();
        Instruction instruction = instructionBuilder.build();
        when(njamsMock.getProcessFromConfiguration(TestInstructionBuilder.PROCESSPATH_VALUE)).thenReturn(configuration.getProcess(TestInstructionBuilder.PROCESSPATH_VALUE));

        Map<String, ProcessConfiguration> processes = configuration.getProcesses();
        when(njamsMock.getProcessesFromConfiguration()).thenReturn(processes);

        setLogLevelProcessor.processInstruction(instruction);

        ProcessConfiguration newlyCreatedProcess = processes.get(TestInstructionBuilder.PROCESSPATH_VALUE);

        verify(njamsMock).setLogModeToConfiguration(logModeToSet);
        assertEquals(logLevelToSet, newlyCreatedProcess.getLogLevel());
        assertEquals(TestInstructionBuilder.EXCLUDED_VALUE, newlyCreatedProcess.isExclude());

        verify(setLogLevelProcessor).saveConfiguration(any());

        Response response = instruction.getResponse();
        assertEquals(0, response.getResultCode());
        assertEquals("Success", response.getResultMessage());
        assertNull(response.getDateTime());
        assertTrue(response.getParameters().isEmpty());
    }
}