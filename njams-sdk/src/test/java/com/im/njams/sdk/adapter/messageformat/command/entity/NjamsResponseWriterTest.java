/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static java.util.Collections.EMPTY_MAP;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NjamsResponseWriterTest {

    private static final ResultCode TEST_RESULT_CODE = ResultCode.SUCCESS;
    private static final String TEST_RESULT_MESSAGE = "test";
    private static final LocalDateTime TEST_LOCAL_DATE_TIME = DateTimeUtility.now();
    private static final String TEST_PARAMETER_KEY = "key";
    private static final String TEST_PARAMETER_VALUE = "value";

    private Response responseSpy;

    private Map<String, String> mockedMap;

    private NjamsResponseWriter njamsResponseWriter;

    private Instruction instructionMock;

    private NjamsResponseWriter njamsResponseWriterWithNull;

    @Before
    public void initialize() {
        responseSpy = spy(new Response());
        mockedMap = mock(Map.class);
        when(responseSpy.getParameters()).thenReturn(mockedMap);
        instructionMock = mock(Instruction.class);
        when(instructionMock.getResponse()).thenReturn(responseSpy);
        njamsResponseWriter = spy(new NjamsResponseWriter<>(instructionMock));
        njamsResponseWriterWithNull = spy(new NjamsResponseWriter<>(null));
    }

//IsEmpty test

    @Test
    public void isEmptyIsTrueIfUnderlyingResponseIsNull() {
        assertTrue(njamsResponseWriterWithNull.isEmpty());
    }

    @Test
    public void isEmptyIsFalseIfResponseHasBeenChanged() {
        when(responseSpy.getResultCode()).thenReturn(ResultCode.ERROR.getResultCode());
        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void responseDefaultConstructorIsLikeThis(){
        Response response = new Response();
        assertEquals(response.getResultCode(), ResultCode.SUCCESS.getResultCode());
        assertNull(response.getResultMessage());
        assertNull(response.getDateTime());
        assertEquals(response.getParameters(), EMPTY_MAP);
    }

//SetResultCode tests

    @Test
    public void afterSetResultCodeIsEmptyIsFalse() {
        njamsResponseWriter.setResultCode(TEST_RESULT_CODE);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void setResultCodeSetsResultCodeInUnderlyingResponse() {
        njamsResponseWriter.setResultCode(TEST_RESULT_CODE);

        verify(responseSpy).setResultCode(TEST_RESULT_CODE.getResultCode());
    }

    @Test(expected = NullPointerException.class)
    public void setResultCodeThrowsNullPointer() {
        njamsResponseWriterWithNull.setResultCode(TEST_RESULT_CODE);
    }

//SetResultMessage tests

    @Test
    public void afterSetResultMessageIsEmptyIsFalse() {
        njamsResponseWriter.setResultMessage(TEST_RESULT_MESSAGE);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void setResultMessageSetsResultMessageInUnderlyingResponse() {
        njamsResponseWriter.setResultMessage(TEST_RESULT_MESSAGE);

        verify(responseSpy).setResultMessage(TEST_RESULT_MESSAGE);
    }

    @Test(expected = NullPointerException.class)
    public void setResultMessageThrowsNullPointer() {
        njamsResponseWriterWithNull.setResultMessage(TEST_RESULT_MESSAGE);
    }

//SetResultCodeAndResultMessage tests

    @Test
    public void afterSetResultCodeAndResultMessageIsEmptyIsFalse() {
        njamsResponseWriter.setResultCodeAndResultMessage(TEST_RESULT_CODE, TEST_RESULT_MESSAGE);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void setResultCodeAndResultMessageSetsResultCodeAndResultMessageInUnderlyingResponse() {
        njamsResponseWriter.setResultCodeAndResultMessage(TEST_RESULT_CODE, TEST_RESULT_MESSAGE);

        verify(responseSpy).setResultCode(TEST_RESULT_CODE.getResultCode());
        verify(responseSpy).setResultMessage(TEST_RESULT_MESSAGE);
    }

    @Test(expected = NullPointerException.class)
    public void setResultCodeAndResultMessageThrowsNullPointer() {
        njamsResponseWriterWithNull.setResultCodeAndResultMessage(TEST_RESULT_CODE, TEST_RESULT_MESSAGE);
    }

//SetDateTime tests

    @Test
    public void afterSetDateTimeIsEmptyIsFalse() {
        njamsResponseWriter.setDateTime(TEST_LOCAL_DATE_TIME);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void setDateTimeSetsDateTimeInUnderlyingResponse() {
        njamsResponseWriter.setDateTime(TEST_LOCAL_DATE_TIME);

        verify(responseSpy).setDateTime(TEST_LOCAL_DATE_TIME);
    }

    @Test(expected = NullPointerException.class)
    public void setDateTimeThrowsNullPointer() {
        njamsResponseWriterWithNull.setDateTime(TEST_LOCAL_DATE_TIME);
    }

//PutParameter tests

    @Test
    public void afterPutParameterIsEmptyIsFalse() {
        njamsResponseWriter.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void putParameterPutsParameterInUnderlyingResponseMap() {
        njamsResponseWriter.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseSpy).getParameters();
        verify(mockedMap).put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseSpy, times(0)).setParameters(any());
    }

    @Test
    public void putParameterCreatesUnderlyingParametersMapIfNotExistent() {
        when(responseSpy.getParameters()).thenReturn(null);

        njamsResponseWriter.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseSpy).getParameters();
        verify(responseSpy).setParameters(any());
    }

    @Test(expected = NullPointerException.class)
    public void putParameterThrowsNullPointer() {
        njamsResponseWriterWithNull.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
    }

//SetParameters tests

    @Test
    public void afterSetParametersIsEmptyIsFalse() {
        njamsResponseWriter.setParameters(mockedMap);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void setParametersSetsTheParametersInUnderlyingResponse() {
        njamsResponseWriter.setParameters(EMPTY_MAP);

        verify(responseSpy).setParameters(EMPTY_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void setParametersThrowsNullPointer() {
        njamsResponseWriterWithNull.setParameters(mockedMap);
    }

//AddParameters tests

    @Test
    public void afterAddParametersIsEmptyIsFalse() {
        njamsResponseWriter.addParameters(EMPTY_MAP);

        assertFalse(njamsResponseWriter.isEmpty());
    }

    @Test
    public void addParametersPutsAllParametersInUnderlyingResponseMap() {
        njamsResponseWriter.addParameters(EMPTY_MAP);
        verify(mockedMap).putAll(EMPTY_MAP);
        verify(responseSpy, times(0)).setParameters(any());
    }

    @Test
    public void addParameterSetsParametersInUnderlyingParametersMapIfNotExist() {
        when(responseSpy.getParameters()).thenReturn(null);

        njamsResponseWriter.addParameters(EMPTY_MAP);
        verify(responseSpy).setParameters(EMPTY_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void addParametersThrowsNullPointer() {
        njamsResponseWriterWithNull.addParameters(mockedMap);
    }

//GetThis tests

    @Test
    public void getThisReturnsTheActualObject() {
        com.im.njams.sdk.adapter.messageformat.command.entity.NjamsResponseWriter abstractResponseWriterImplCalledWithThis = njamsResponseWriter
                .getThis();
        assertEquals(njamsResponseWriter, abstractResponseWriterImplCalledWithThis);
    }

//ToString tests

    @Test
    public void checkToStringForResponseWriter() throws IOException {
        Response responseToSet = createRealResponse();
        when(instructionMock.getResponse()).thenReturn(responseToSet);
        njamsResponseWriter = new NjamsResponseWriter(instructionMock);

        String responseAsString = njamsResponseWriter.toString();
        System.out.println(responseAsString);
        assertToStringCreatesSerializableOutput(responseToSet, responseAsString);
    }

    private Response createRealResponse() {
        Response response = new Response();
        response.setResultCode(TEST_RESULT_CODE.getResultCode());
        response.setResultMessage(TEST_RESULT_MESSAGE);
        response.setDateTime(TEST_LOCAL_DATE_TIME);
        response.getParameters().put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        return response;
    }

    private void assertToStringCreatesSerializableOutput(Response responseThatWasSerialized, String serializedResponse)
            throws IOException {
        Response parsedResponse = JsonUtils.parse(serializedResponse, Response.class);
        assertNotEquals(responseThatWasSerialized, parsedResponse);
        assertEquals(responseThatWasSerialized.getResultCode(), parsedResponse.getResultCode());
        assertEquals(responseThatWasSerialized.getResultMessage(), parsedResponse.getResultMessage());
        assertEquals(responseThatWasSerialized.getDateTime(), parsedResponse.getDateTime());
        assertTrue(responseThatWasSerialized.getParameters().containsKey(TEST_PARAMETER_KEY));
        assertTrue(parsedResponse.getParameters().containsKey(TEST_PARAMETER_KEY));
        assertEquals(responseThatWasSerialized.getParameters().get(TEST_PARAMETER_KEY),
                parsedResponse.getParameters().get(TEST_PARAMETER_KEY));
    }

    @Test
    public void toStringReturnsEmptyJsonForNullResponse() {
        String s = njamsResponseWriterWithNull.toString();
        assertEquals(s, "null");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void toStringThrowsNjamsSdkRuntimeExceptionWithMockedResponse() {
        njamsResponseWriter.toString();
    }

}