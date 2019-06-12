package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import org.junit.Test;

import static com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessorTestUtility.prepareGetLogLevelInstruction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FallbackProcessorTest {

    private FallbackProcessor fallbackProcessor;

    public FallbackProcessorTest(){
        fallbackProcessor = new FallbackProcessor();
    }

    @Test
    public void testNullInstruction(){
        fallbackProcessor.processInstruction(null);
    }

    @Test
    public void testNullRequest(){
        Instruction instruction = prepareGetLogLevelInstruction();
        instruction.setRequest(null);
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.REQUEST_IS_NULL);
    }

    private void checkForCorrectProcessing(Instruction instruction, String errorMessage) {
        Response response = instruction.getResponse();
        assertEquals(response.getResultMessage(), errorMessage);
        assertEquals(response.getResultCode(), FallbackProcessor.ERROR_RESULT_CODE);
        assertTrue(response.getParameters().isEmpty());
        assertEquals(response.getDateTime(), null);
    }

    @Test
    public void testNullCommand(){
        Instruction instruction = prepareGetLogLevelInstruction();
        instruction.getRequest().setCommand(null);
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_IS_NULL);
    }

    @Test
    public void testEmptyCommand(){
        Instruction instruction = prepareGetLogLevelInstruction();
        instruction.getRequest().setCommand("");
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_IS_EMPTY);
    }

    @Test
    public void testNormalCommand(){
        Instruction instruction = prepareGetLogLevelInstruction();
        fallbackProcessor.processInstruction(instruction);
        checkForCorrectProcessing(instruction, FallbackProcessor.COMMAND_UNKNOWN + instruction.getRequest().getCommand());
    }
}