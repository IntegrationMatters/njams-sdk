package com.im.njams.sdk.adapter.messageformat.command.control;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ReplayInstruction;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NjamsInstructionWrapperTest {

    public NjamsInstructionWrapper instructionWrapper;

    public Instruction instructionMock;

    private static List<String> checkedCommands = new ArrayList<>();

    @Before
    public void initialize(){
        instructionMock = mock(Instruction.class);
        instructionWrapper = new NjamsInstructionWrapper(instructionMock);
    }

    @AfterClass
    public static void checkAllCommands(){
        Arrays.stream(Command.values()).forEach(command -> assertTrue(checkedCommands.contains(command.commandString())));
    }

//Wrap tests

//DefaultInstructions
    @Test
    public void wrapNullGivesADefaultInstruction(){
        NjamsInstructionWrapper nullWrapper = new NjamsInstructionWrapper(null);
        assertTrue(nullWrapper.wrap() instanceof DefaultInstruction);
    }

    private void assertDefaultFor(String commandToUse){
        when(instructionMock.getCommand()).thenReturn(commandToUse);
        assertTrue(instructionWrapper.wrap() instanceof DefaultInstruction);
        checkedCommands.add(commandToUse);
    }

    @Test
    public void wrapInstructionWithoutCommand(){
        assertDefaultFor(null);
    }

    @Test
    public void wrapInstructionWithEmptyCommand(){
        assertDefaultFor("");
    }

    @Test
    public void wrapInstructionWithInvalidCommand(){
        assertDefaultFor("InvalidCommand");
    }

    @Test
    public void wrapInstructionWithSendProjectMessageCommand(){
        assertDefaultFor(Command.SEND_PROJECTMESSAGE.commandString());
    }

    @Test
    public void wrapInstructionWithTestExpressionCommand(){
        assertDefaultFor(Command.TEST_EXPRESSION.commandString());
    }

    @Test
    public void multipleWrapCallsReturnTheSameWrappedInstruction(){
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInstruction = instructionWrapper.wrap();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction wrappedInstruction2 = instructionWrapper.wrap();
        assertEquals(wrappedInstruction, wrappedInstruction2);
    }

//ConditionInstructions

    @Test
    public void wrapInstructionWithGetLogLevelCommand(){
        assertConditionFor(Command.GET_LOG_LEVEL.commandString());
    }

    private void assertConditionFor(String commandToUse){
        when(instructionMock.getCommand()).thenReturn(commandToUse);
        assertTrue(instructionWrapper.wrap() instanceof ConditionInstruction);
        checkedCommands.add(commandToUse);
    }

    @Test
    public void wrapInstructionWithSetLogLevelCommand(){
        assertConditionFor(Command.SET_LOG_LEVEL.commandString());
    }

    @Test
    public void wrapInstructionWithGetLogModeCommand(){
        assertConditionFor(Command.GET_LOG_MODE.commandString());
    }

    @Test
    public void wrapInstructionWithSetLogModeCommand(){
        assertConditionFor(Command.SET_LOG_MODE.commandString());
    }

    @Test
    public void wrapInstructionWithSetTracingCommand(){
        assertConditionFor(Command.SET_TRACING.commandString());
    }

    @Test
    public void wrapInstructionWithGetTracingCommand(){
        assertConditionFor(Command.GET_TRACING.commandString());
    }

    @Test
    public void wrapInstructionWithConfigureExtractCommand(){
        assertConditionFor(Command.CONFIGURE_EXTRACT.commandString());
    }

    @Test
    public void wrapInstructionWithGetExtractCommand(){
        assertConditionFor(Command.GET_EXTRACT.commandString());
    }

    @Test
    public void wrapInstructionWithDeleteExtractCommand(){
        assertConditionFor(Command.DELETE_EXTRACT.commandString());
    }

    @Test
    public void wrapInstructionWithRecordCommand(){
        assertConditionFor(Command.RECORD.commandString());
    }

//ReplayInstruction

    @Test
    public void wrapInstructionWithReplayCommand(){
        assertReplayFor(Command.REPLAY.commandString());
    }

    private void assertReplayFor(String commandToUse){
        when(instructionMock.getCommand()).thenReturn(commandToUse);
        assertTrue(instructionWrapper.wrap() instanceof ReplayInstruction);
        checkedCommands.add(commandToUse);
    }

}