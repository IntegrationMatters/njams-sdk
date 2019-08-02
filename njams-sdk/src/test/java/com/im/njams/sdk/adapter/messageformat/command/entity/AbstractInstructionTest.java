package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Test;

import static org.junit.Assert.*;

public class AbstractInstructionTest {


    @Test
    public void testInstructionWithNullResponseHasAResponseAfterResultCodeWasSet(){
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultCode(ResultCode.SUCCESS);
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }

    @Test
    public void testInstructionWithNullResponseHasAResponseAfterResultMessageWasSet(){
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultMessage("Test");
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }

    @Test
    public void testInstructionWithNullResponseHasAResponseAfterSetParameters(){
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultCode(ResultCode.SUCCESS);
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }

    @Test
    public void testInstructionWithNullResponseHasAResponseAfterPutParameter(){
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultCode(ResultCode.SUCCESS);
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }

    @Test
    public void testInstructionWithNullResponseHasAResponseAfterAddParameter(){
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultCode(ResultCode.SUCCESS);
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }
}