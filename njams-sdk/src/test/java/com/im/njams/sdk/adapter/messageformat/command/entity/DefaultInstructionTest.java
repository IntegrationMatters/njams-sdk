package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Request;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultInstructionTest {

    private static final String EMPTY_STRING = "";

    private Instruction instructionMock;

    private Request requestMock;

    private Response responseMock;

    private DefaultInstruction defaultInstruction;

    @Before
    public void initialize() {
        instructionMock = mock(Instruction.class);
        requestMock = mock(Request.class);
        responseMock = mock(Response.class);
        fillRequestAndResponse(requestMock, responseMock);
        defaultInstruction = spy(new DefaultInstruction(instructionMock));
    }

    private void fillRequestAndResponse(Request request, Response response) {
        when(instructionMock.getRequest()).thenReturn(request);
        when(instructionMock.getResponse()).thenReturn(response);
    }

//CreateRequestReader tests

    @Test
    public void createRequestReaderInstanceCreatesDefaultRequestReader() {
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction
                .createRequestReaderInstance(mock(Request.class));
        assertTrue(requestReader instanceof DefaultInstruction.DefaultRequestReader);
    }

    @Test
    public void createRequestReaderInstanceWithNullRequest() {
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.createRequestReaderInstance(null);
        assertTrue(requestReader instanceof DefaultInstruction.DefaultRequestReader);
    }

//CreateResponseWriter tests

    @Test
    public void createResponseWriterInstanceCreatesDefaultResponseWriter() {
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction
                .createResponseWriterInstance(mock(Response.class));
        assertTrue(responseWriter instanceof DefaultInstruction.DefaultResponseWriter);
    }

    @Test
    public void createResponseWriterInstanceWithNullResponse() {
        DefaultInstruction.DefaultResponseWriter responseWriter = defaultInstruction.createResponseWriterInstance(null);
        assertTrue(responseWriter instanceof DefaultInstruction.DefaultResponseWriter);
    }

//DefaultRequestReader tests

    @Test
    public void instructionWithNullRequestIsEmpty() {
        fillRequestAndResponse(null, null);
        assertNull(instructionMock.getRequest());
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, true, true, true, EMPTY_STRING);
    }

    private void assertRequestReaderFields(DefaultInstruction.DefaultRequestReader requestReader,
            boolean shouldRequestBeEmpty, boolean shouldCommandBeNull, boolean shouldCommandBeEmpty,
            String commandName) {
        assertEquals(shouldRequestBeEmpty, requestReader.isEmpty());
        assertEquals(shouldCommandBeNull, requestReader.isCommandNull());
        assertEquals(shouldCommandBeEmpty, requestReader.isCommandEmpty());
        assertEquals(commandName, requestReader.getCommand());
    }

    @Test
    public void instructionWithNullCommand(){
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, true, true, EMPTY_STRING);
    }

    @Test
    public void instructionWithEmptyCommand(){
        when(requestMock.getCommand()).thenReturn(EMPTY_STRING);
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, false, true, EMPTY_STRING);
    }

    @Test
    public void instructionWithCommand(){
        String testString = "test";
        when(requestMock.getCommand()).thenReturn(testString);
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, false, false, testString);
    }

    @Test
    public void instructionWithoutParameters(){
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertTrue(requestReader.getParameters().isEmpty());
    }

    @Test
    public void instructionWithParameters(){
        Map<String, String> params = new HashMap<>();
        String key = "key";
        String value = "value";
        params.put(key, value);
        when(requestMock.getParameters()).thenReturn(params);
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertFalse(requestReader.getParameters().isEmpty());
        assertEquals(value, requestReader.getParameter(key));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void instructionWithoutRequestWillGetAnEmptyParametersMapThatCantBeFilled(){
        fillRequestAndResponse(null, null);

        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        Map<String, String> emptyParameters = requestReader.getParameters();
        assertTrue(emptyParameters.isEmpty());

        String key = "key";
        String value = "value";
        emptyParameters.put(key, value);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void parametersCantBeModified(){
        Map<String, String> params = new HashMap<>();
        String key = "key";
        String value = "value";
        params.put(key, value);
        when(requestMock.getParameters()).thenReturn(params);
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        Map<String, String> unmodifiableParameters = requestReader.getParameters();
        assertEquals(value, requestReader.getParameter(key));

        unmodifiableParameters.put(key, "anotherValue");
    }

    @Test
    public void checkToStringForRequestReader(){
        fillRequestAndResponse(new Request(), null);
        DefaultInstruction.DefaultRequestReader requestReader = defaultInstruction.getRequestReader();
        String requestAsString = requestReader.toString();
        System.out.println(requestAsString);
    }

//DefaultResponseWriter tests

    @Test
    public void setResultCodeAndResultMessage(){
        DefaultInstruction.DefaultResponseWriter responseWriter = spy(defaultInstruction.getResponseWriter());
        final String resultMessage = "test";
        responseWriter.setResultCodeAndResultMessage(ResultCode.WARNING, resultMessage);
        assertFalse(responseWriter.isEmpty());
        verify(responseWriter).setResultCode(ResultCode.WARNING);
        verify(responseWriter).setResultMessage(resultMessage);
    }
}