package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction.DEFAULT_SUCCESS_MESSAGE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbstractInstructionTest {

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
        assertEquals(response.getResultCode(), 0);
        assertEquals(response.getResultMessage(), DEFAULT_SUCCESS_MESSAGE);
    }

    @Test
    public void getResponseWriterWithNormalInstruction() {
        Response response = new Response();
        getResponseWriter(false, response);
        assertEquals(normalInstruction.getResponse(), response);

        verify(instructionWithNormalInstruction).createResponseWriterInstance(response);
    }

//AbstractResponseWriter tests

    @Test
    public void testInstructionWithNullResponseHasAResponseAfterResultCodeWasSet() {
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
    public void testInstructionWithNullResponseHasAResponseAfterResultMessageWasSet() {
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
    public void testInstructionWithNullResponseHasAResponseAfterSetParameters() {
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
    public void testInstructionWithNullResponseHasAResponseAfterPutParameter() {
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
    public void testInstructionWithNullResponseHasAResponseAfterAddParameter() {
        Instruction instruction = new Instruction();
        assertNull(instruction.getResponse());
        DefaultInstruction defaultInstruction = new DefaultInstruction(instruction);
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.getResponseWriter();
        assertTrue(responseWriter.isEmpty());

        responseWriter.setResultCode(ResultCode.SUCCESS);
        assertFalse(responseWriter.isEmpty());
        assertNotNull(instruction.getResponse());
    }

    private class AbstractInstructionImpl extends AbstractInstruction<AbstractRequestReaderImpl,
            AbstractInstruction.AbstractResponseWriter> {

        public AbstractInstructionImpl(Instruction messageFormatInstruction) {
            super(messageFormatInstruction);
        }

        @Override
        protected AbstractRequestReaderImpl createRequestReaderInstance(Request request) {
            return new AbstractRequestReaderImpl();
        }

        @Override
        protected AbstractResponseWriter createResponseWriterInstance(Response response) {
            return new AbstractResponseWriter(response);
        }
    }

    private class AbstractRequestReaderImpl implements com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader {

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean isCommandNull() {
            return false;
        }

        @Override
        public boolean isCommandEmpty() {
            return false;
        }

        @Override
        public String getCommand() {
            return null;
        }

        @Override
        public Map<String, String> getParameters() {
            return null;
        }

        @Override
        public String getParameter(String paramKey) {
            return null;
        }
    }
}