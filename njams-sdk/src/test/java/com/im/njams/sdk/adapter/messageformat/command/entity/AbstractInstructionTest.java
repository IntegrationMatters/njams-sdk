package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbstractInstructionTest {

    private static final ResultCode DEFAULT_SUCCESS_CODE = ResultCode.SUCCESS;

    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    private Instruction instructionMock;

    private Instruction normalInstruction;

    private AbstractInstruction instruction;

    private AbstractInstruction instructionWithNormalInstruction;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
        normalInstruction = new Instruction();
        instruction = spy(new AbstractInstructionImpl(instructionMock));
        instructionWithNormalInstruction = spy(new AbstractInstructionImpl(normalInstruction));
    }

//IsEmpty tests

    @Test
    public void isEmptyWithNull() {
        instruction = new AbstractInstructionImpl(null);
        assertTrue(instruction.isEmpty());
    }

    @Test
    public void isEmptyWithInstruction() {
        assertFalse(instruction.isEmpty());
    }

//GetRealInstruction tests

    @Test
    public void getRealInstruction() {
        assertEquals(instructionMock, instruction.getRealInstruction());
    }

//GetRequestReader tests

    @Test
    public void getRequestReaderWithNullInstruction() {
        getRequestReader(true, null);
    }

    private void getRequestReader(boolean isEmpty, Request request) {
        doReturn(isEmpty).when(instruction).isEmpty();
        when(instructionMock.getRequest()).thenReturn(request);

        instruction.getRequestReader();

        verify(instruction).createRequestReaderInstance(request);
    }

    @Test
    public void getRequestReaderWithInstructionButWithoutRequest() {
        getRequestReader(false, null);
    }

    @Test
    public void getRequestReaderWithNormalInstruction() {
        Request request = new Request();
        getRequestReader(false, request);
    }

    @Test
    public void getRequestReaderTwiceGivesTheSameReader() {
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader requestReader1 = instruction
                .getRequestReader();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader requestReader2 = instruction
                .getRequestReader();
        assertEquals(requestReader1, requestReader2);
    }

//GetResponseWriter tests

    @Test
    public void getResponseWriterWithNullInstruction() {
        getResponseWriter(true, null);
        assertNull(normalInstruction.getResponse());

        verify(instructionWithNormalInstruction).createResponseWriterInstance(null);
    }

    private void getResponseWriter(boolean isEmpty, Response response) {
        doReturn(isEmpty).when(instructionWithNormalInstruction).isEmpty();
        normalInstruction.setResponse(response);

        instructionWithNormalInstruction.getResponseWriter();
    }

    @Test
    public void getResponseWriterWithInstructionButWithoutDefaultSuccessResponse() {
        getResponseWriter(false, null);
        Response response = normalInstruction.getResponse();
        assertNotNull(response);
        assertEquals(response.getResultCode(), DEFAULT_SUCCESS_CODE.getResultCode());
        assertEquals(response.getResultMessage(), DEFAULT_SUCCESS_MESSAGE);
    }

    @Test
    public void getResponseWriterWithNormalInstruction() {
        Response response = new Response();
        getResponseWriter(false, response);
        assertEquals(normalInstruction.getResponse(), response);

        verify(instructionWithNormalInstruction).createResponseWriterInstance(response);
    }

    @Test
    public void getResponseWriterTwiceGivesTheSameWriter() {
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction.ResponseWriter responseWriter1 = instruction
                .getResponseWriter();
        com.im.njams.sdk.api.adapter.messageformat.command.Instruction.ResponseWriter responseWriter2 = instruction
                .getResponseWriter();
        assertEquals(responseWriter1, responseWriter2);
    }

//Private Helper Classes

    private class AbstractInstructionImpl extends AbstractInstruction<AbstractRequestReaderImpl,
            AbstractResponseWriter> {

        public AbstractInstructionImpl(Instruction messageFormatInstruction) {
            super(messageFormatInstruction);
        }

        @Override
        protected AbstractRequestReaderImpl createRequestReaderInstance(Request request) {
            return new AbstractRequestReaderImpl(request);
        }

        @Override
        protected AbstractResponseWriterImpl createResponseWriterInstance(Response response) {
            return new AbstractResponseWriterImpl(response);
        }
    }

    private class AbstractRequestReaderImpl extends AbstractRequestReader {

        protected AbstractRequestReaderImpl(Request requestToRead) {
            super(requestToRead);
        }
    }

    private class AbstractResponseWriterImpl<T extends AbstractResponseWriterImpl<T>> extends AbstractResponseWriter<T> {

        protected AbstractResponseWriterImpl(Response response) {
            super(response);
        }
    }
}