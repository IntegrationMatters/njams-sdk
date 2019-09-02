package com.im.njams.sdk.adapter.messageformat.command.boundary;

import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.api.adapter.messageformat.command.Instruction;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NjamsInstructionFactoryTest {

    private NjamsInstructionFactory instructionFactory;

    private static final com.faizsiegeln.njams.messageformat.v4.command.Instruction EMPTY_INSTRUCTION = new com.faizsiegeln.njams.messageformat.v4.command.Instruction();

    private static final Request REQUEST = new Request();

    private static final Response EMPTY_RESPONSE = new Response();

    private static final Response FILLED_RESPONSE = new Response();

    private static final ResultCode TEST_RESULT_CODE = ResultCode.ERROR;

    private static final com.faizsiegeln.njams.messageformat.v4.command.Instruction INSTRUCTION_WITH_REQUEST;

    private static final com.faizsiegeln.njams.messageformat.v4.command.Instruction INSTRUCTION_WITH_RESPONSE;

    private static final com.faizsiegeln.njams.messageformat.v4.command.Instruction INSTRUCTION;

    static {
        INSTRUCTION_WITH_REQUEST = new com.faizsiegeln.njams.messageformat.v4.command.Instruction();
        INSTRUCTION_WITH_REQUEST.setRequest(REQUEST);
        INSTRUCTION_WITH_RESPONSE = new com.faizsiegeln.njams.messageformat.v4.command.Instruction();
        FILLED_RESPONSE.setResultCode(TEST_RESULT_CODE.getResultCode());
        INSTRUCTION = new com.faizsiegeln.njams.messageformat.v4.command.Instruction();
        INSTRUCTION.setRequest(REQUEST);
        INSTRUCTION.setResponse(EMPTY_RESPONSE);
    }

    public NjamsInstructionFactoryTest(){
        instructionFactory = new NjamsInstructionFactory();
    }

    @Before
    public void resetInstructionResponse(){
        INSTRUCTION.setResponse(EMPTY_RESPONSE);
        INSTRUCTION_WITH_RESPONSE.setResponse(EMPTY_RESPONSE);
    }

//GetInstructionOf(String) tests

    @Test
    public void nullStringShouldReturnAnEmptyInstruction() throws NjamsInstructionException {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf((String) null);
        assertEmptyButNotNull(wrappedInstruction, true, true, true);

    }

    private void assertEmptyButNotNull(Instruction wrappedInstruction, boolean isInstructionEmpty, boolean isRequestReaderEmpty, boolean isResponseWriterEmpty){
        assertNotNull(wrappedInstruction);
        assertEquals(isInstructionEmpty, wrappedInstruction.isEmpty());
        assertEquals(isRequestReaderEmpty, wrappedInstruction.getRequestReader().isEmpty());
        assertEquals(isResponseWriterEmpty, wrappedInstruction.getResponseWriter().isEmpty());
    }

    @Test
    public void nullAsContentShouldReturnAnEmptyInstruction() throws NjamsInstructionException {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf("null");
        assertEmptyButNotNull(wrappedInstruction, true, true, true);
    }

    @Test(expected = NjamsInstructionException.class)
    public void invalidInstructionContentShouldThrowAnParsingException() throws NjamsInstructionException {
        instructionFactory.getInstructionOf("invalid");
    }

    @Test
    public void validInstructionContentShouldReturnANotEmptyInstruction()
            throws NjamsInstructionException, JsonProcessingException {
        String serializedInstruction = JsonUtils.serialize(EMPTY_INSTRUCTION);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, true, true);
    }

    @Test
    public void validInstructionContentWithRequestButNoResponse() throws JsonProcessingException, NjamsInstructionException {
        String serializedInstruction = JsonUtils.serialize(INSTRUCTION_WITH_REQUEST);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, false, true);
    }

    @Test
    public void validInstructionContentWithEmptyResponseButNoRequest() throws JsonProcessingException, NjamsInstructionException {
        String serializedInstruction = JsonUtils.serialize(INSTRUCTION_WITH_RESPONSE);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, true, true);
    }

    @Test
    public void validInstructionContentWithFilledResponseButNoRequest() throws JsonProcessingException, NjamsInstructionException {
        INSTRUCTION_WITH_RESPONSE.setResponse(FILLED_RESPONSE);
        String serializedInstruction = JsonUtils.serialize(INSTRUCTION_WITH_RESPONSE);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, true, false);
    }

    @Test
    public void validInstructionContentWithRequestAndEmptyResponse() throws JsonProcessingException, NjamsInstructionException {
        String serializedInstruction = JsonUtils.serialize(INSTRUCTION);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, false, true);
    }

    @Test
    public void validInstructionContentWithRequestAndFilledResponse() throws JsonProcessingException, NjamsInstructionException {
        INSTRUCTION.setResponse(FILLED_RESPONSE);
        String serializedInstruction = JsonUtils.serialize(INSTRUCTION);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(serializedInstruction);
        assertEmptyButNotNull(wrappedInstruction, false, false, false);
    }

//GetInstructionOf(Instruction) tests

    @Test
    public void nullInstructionShouldReturnAnEmptyInstruction() {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf((com.faizsiegeln.njams.messageformat.v4.command.Instruction) null);
        assertEmptyButNotNull(wrappedInstruction, true, true, true);
    }

    @Test
    public void validInstructionShouldReturnANotEmptyInstruction() {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(EMPTY_INSTRUCTION);
        assertEmptyButNotNull(wrappedInstruction, false, true, true);
    }

    @Test
    public void validInstructionWithRequestButNoResponse(){
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(INSTRUCTION_WITH_REQUEST);
        assertEmptyButNotNull(wrappedInstruction, false, false, true);
    }

    @Test
    public void validInstructionWithEmptyResponseButNoRequest() {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(INSTRUCTION_WITH_RESPONSE);
        assertEmptyButNotNull(wrappedInstruction, false, true, true);
    }

    @Test
    public void validInstructionWithFilledResponseButNoRequest() {
        INSTRUCTION_WITH_RESPONSE.setResponse(FILLED_RESPONSE);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(INSTRUCTION_WITH_RESPONSE);
        assertEmptyButNotNull(wrappedInstruction, false, true, false);
    }

    @Test
    public void validInstructionWithRequestAndEmptyResponse() {
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(INSTRUCTION);
        assertEmptyButNotNull(wrappedInstruction, false, false, true);
    }

    @Test
    public void validInstructionWithRequestAndFilledResponse() {
        INSTRUCTION.setResponse(FILLED_RESPONSE);
        Instruction wrappedInstruction = instructionFactory.getInstructionOf(INSTRUCTION);
        assertEmptyButNotNull(wrappedInstruction, false, false, false);
    }
}