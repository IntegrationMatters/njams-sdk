package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.control.processor.TestInstructionBuilder;
import org.junit.Before;
import org.junit.Test;

import static com.faizsiegeln.njams.messageformat.v4.command.Command.SET_LOG_MODE;
import static org.mockito.Mockito.*;

public class SetLogModeProcessorTest extends AbstractConfigurationProcessor{

    private SetLogModeProcessor setLogModeProcessor;

    @Before
    public void setNewProcessor() {
        setLogModeProcessor = spy(new SetLogModeProcessor(njamsMock));
    }

    @Test
    public void setLogModeWithoutLogMode() {
        instructionBuilder.prepareInstruction(SET_LOG_MODE);
        checkResultMessageForMissingsParameters(setLogModeProcessor, TestInstructionBuilder.LOG_MODE_KEY);
    }

    @Test
    public void setLogModeWithInvalidLogMode(){
        instructionBuilder.prepareInstruction(SET_LOG_MODE).addLogMode("INVALID");
        checkResultMessageForMissingsParameters(setLogModeProcessor, TestInstructionBuilder.LOG_MODE_KEY);
    }

    @Test
    public void setLogMode(){
        instructionBuilder.prepareInstruction(SET_LOG_MODE).addDefaultLogMode();
        Instruction instruction = instructionBuilder.build();

        setLogModeProcessor.processInstruction(instruction);

        verify(njamsMock).setLogModeToConfiguration(TestInstructionBuilder.LOG_MODE_VALUE);
        verify(setLogModeProcessor).saveConfiguration(any());
    }
}