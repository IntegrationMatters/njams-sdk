/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity.defaults;

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
        DefaultRequestReader requestReader = defaultInstruction
                .createRequestReaderInstance(mock(Request.class));
        assertTrue(requestReader instanceof DefaultRequestReader);
    }

    @Test
    public void createRequestReaderInstanceWithNullRequest() {
        DefaultRequestReader requestReader = defaultInstruction.createRequestReaderInstance(null);
        assertTrue(requestReader instanceof DefaultRequestReader);
    }

//CreateResponseWriter tests

    @Test
    public void createResponseWriterInstanceCreatesDefaultResponseWriter() {
        DefaultResponseWriter responseWriter = defaultInstruction
                .createResponseWriterInstance(mock(Response.class));
        assertTrue(responseWriter instanceof DefaultResponseWriter);
    }

    @Test
    public void createResponseWriterInstanceWithNullResponse() {
        DefaultResponseWriter responseWriter = defaultInstruction.createResponseWriterInstance(null);
        assertTrue(responseWriter instanceof DefaultResponseWriter);
    }

//DefaultRequestReader tests

    @Test
    public void instructionWithNullRequestIsEmpty() {
        fillRequestAndResponse(null, null);
        assertNull(instructionMock.getRequest());
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, true, true, true, EMPTY_STRING);
    }

    private void assertRequestReaderFields(DefaultRequestReader requestReader,
            boolean shouldRequestBeEmpty, boolean shouldCommandBeNull, boolean shouldCommandBeEmpty,
            String commandName) {
        assertEquals(shouldRequestBeEmpty, requestReader.isEmpty());
        assertEquals(shouldCommandBeNull, requestReader.isCommandNull());
        assertEquals(shouldCommandBeEmpty, requestReader.isCommandEmpty());
        assertEquals(commandName, requestReader.getCommand());
    }

    @Test
    public void instructionWithNullCommand(){
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, true, true, EMPTY_STRING);
    }

    @Test
    public void instructionWithEmptyCommand(){
        when(requestMock.getCommand()).thenReturn(EMPTY_STRING);
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, false, true, EMPTY_STRING);
    }

    @Test
    public void instructionWithCommand(){
        String testString = "test";
        when(requestMock.getCommand()).thenReturn(testString);
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertRequestReaderFields(requestReader, false, false, false, testString);
    }

    @Test
    public void instructionWithoutParameters(){
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertTrue(requestReader.getParameters().isEmpty());
    }

    @Test
    public void instructionWithParameters(){
        Map<String, String> params = new HashMap<>();
        String key = "key";
        String value = "value";
        params.put(key, value);
        when(requestMock.getParameters()).thenReturn(params);
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        assertFalse(requestReader.getParameters().isEmpty());
        assertEquals(value, requestReader.getParameter(key));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void instructionWithoutRequestWillGetAnEmptyParametersMapThatCantBeFilled(){
        fillRequestAndResponse(null, null);

        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

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
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();

        Map<String, String> unmodifiableParameters = requestReader.getParameters();
        assertEquals(value, requestReader.getParameter(key));

        unmodifiableParameters.put(key, "anotherValue");
    }

    @Test
    public void checkToStringForRequestReader(){
        fillRequestAndResponse(new Request(), null);
        DefaultRequestReader requestReader = defaultInstruction.getRequestReader();
        String requestAsString = requestReader.toString();
        System.out.println(requestAsString);
    }

//DefaultResponseWriter tests

    @Test
    public void setResultCodeAndResultMessage(){
        DefaultResponseWriter responseWriter = spy(defaultInstruction.getResponseWriter());
        final String resultMessage = "test";
        responseWriter.setResultCodeAndResultMessage(ResultCode.WARNING, resultMessage);
        assertFalse(responseWriter.isEmpty());
        verify(responseWriter).setResultCode(ResultCode.WARNING);
        verify(responseWriter).setResultMessage(resultMessage);
    }
}