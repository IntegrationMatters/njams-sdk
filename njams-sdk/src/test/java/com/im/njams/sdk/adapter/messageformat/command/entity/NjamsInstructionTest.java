package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsInstructionTest {

    private NjamsInstruction njamsInstructionWithoutInstruction;

    private Instruction instructionMockWithoutRequestAndResponse;

    private NjamsInstruction njamsInstructionWithoutRequestAndResponse;

    private Instruction instructionMock;

    private Request requestMock;

    private Response responseMock;

    private NjamsInstruction njamsInstruction;

    @Before
    public void initialize() {
        njamsInstructionWithoutInstruction = spy(new NjamsInstruction(null));

        instructionMockWithoutRequestAndResponse = mock(Instruction.class);
        njamsInstructionWithoutRequestAndResponse = spy(new NjamsInstruction(instructionMockWithoutRequestAndResponse));

        instructionMock = mock(Instruction.class);
        requestMock = mock(Request.class);
        responseMock = mock(Response.class);
        when(instructionMock.getRequest()).thenReturn(requestMock);
        when(instructionMock.getResponse()).thenReturn(responseMock);
        njamsInstruction = spy(new NjamsInstruction(instructionMock));
    }

//Constructor

    @Test
    public void constructorCreatesCorrectReaderAndWriter(){
        assertTrue(njamsInstruction.getRequestReader() instanceof NjamsRequestReader);
        assertTrue(njamsInstruction.getResponseWriter() instanceof NjamsResponseWriter);
    }

//IsEmpty tests

    @Test
    public void isEmptyWithNull() {
        assertTrue(njamsInstructionWithoutInstruction.isEmpty());
    }

    @Test
    public void isNotEmptyWithInstruction() {
        assertFalse(njamsInstructionWithoutRequestAndResponse.isEmpty());
    }

//GetRealInstruction tests

    @Test
    public void getRealInstructionFromNullInstruction() {
        assertNull(njamsInstructionWithoutInstruction.getRealInstruction());
    }

    @Test
    public void getRealInstruction() {
        assertEquals(instructionMockWithoutRequestAndResponse, njamsInstructionWithoutRequestAndResponse.getRealInstruction());
    }

//GetRequestReader tests

    @Test
    public void getRequestReaderWithoutInstruction(){
        NjamsRequestReader requestReader = njamsInstructionWithoutInstruction.getRequestReader();
        assertNotNull(requestReader);
        assertTrue(requestReader.isEmpty());
    }

    @Test
    public void getRequestReaderWithoutRequest(){
        NjamsRequestReader requestReader = njamsInstructionWithoutRequestAndResponse.getRequestReader();
        assertNotNull(requestReader);
        assertTrue(requestReader.isEmpty());
    }

    @Test
    public void getRequestReader(){
        NjamsRequestReader requestReader = njamsInstruction.getRequestReader();
        assertNotNull(requestReader);
        assertFalse(requestReader.isEmpty());
    }

//GetResponseWriter tests

    @Test
    public void getResponseWriterWithoutInstruction(){
        NjamsResponseWriter responseWriter = njamsInstructionWithoutInstruction.getResponseWriter();
        assertNotNull(responseWriter);
        assertTrue(responseWriter.isEmpty());
    }

    @Test
    public void getResponseWriterWithoutResponse(){
        NjamsResponseWriter responseWriter = njamsInstructionWithoutRequestAndResponse.getResponseWriter();
        assertNotNull(responseWriter);
        assertTrue(responseWriter.isEmpty());
    }

    @Test
    public void getResponseWriterWithEmptyResponse(){
        NjamsResponseWriter responseWriter = njamsInstruction.getResponseWriter();
        assertNotNull(responseWriter);
        assertTrue(responseWriter.isEmpty());
    }

    @Test
    public void getResponseWriterWithResponse(){
        when(responseMock.getResultCode()).thenReturn(ResultCode.ERROR.getResultCode());
        NjamsResponseWriter responseWriter = njamsInstruction.getResponseWriter();
        assertNotNull(responseWriter);
        assertFalse(responseWriter.isEmpty());
    }

}