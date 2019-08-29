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

import com.faizsiegeln.njams.messageformat.v4.command.Response;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbstractResponseWriterTest {

    private static final ResultCode TEST_RESULT_CODE = ResultCode.SUCCESS;
    private static final String TEST_RESULT_MESSAGE = "test";
    private static final LocalDateTime TEST_LOCAL_DATE_TIME = DateTimeUtility.now();
    private static final String TEST_PARAMETER_KEY = "key";
    private static final String TEST_PARAMETER_VALUE = "value";

    private Response responseMock;

    private Map<String, String> mockedMap;

    private AbstractResponseWriterImpl abstractResponseWriterImpl;

    private AbstractResponseWriterImpl abstractResponseWriterWithNull;

    @Before
    public void initialize() {
        responseMock = mock(Response.class);
        mockedMap = mock(Map.class);
        when(responseMock.getParameters()).thenReturn(mockedMap);
        abstractResponseWriterImpl = spy(new AbstractResponseWriterImpl<>(responseMock));
        abstractResponseWriterWithNull = spy(new AbstractResponseWriterImpl<>(null));
    }

//IsEmpty test

    @Test
    public void isEmptyIsTrueIfUnderlyingResponseIsNull() {
        assertTrue(abstractResponseWriterWithNull.isEmpty());
        abstractResponseWriterWithNull.setResponseIsActuallyEmpty();
        assertTrue(true);
    }

    @Test
    public void isEmptyIsTrueIfIsActuallyEmptyIsTrue() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();
        assertTrue(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void isEmptyIsFalseIfIsActuallyEmptyIsFalse() {
        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

//SetResultCode tests

    @Test
    public void afterSetResultCodeIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.setResultCode(TEST_RESULT_CODE);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void setResultCodeSetsResultCodeInUnderlyingResponse() {
        abstractResponseWriterImpl.setResultCode(TEST_RESULT_CODE);

        verify(responseMock).setResultCode(TEST_RESULT_CODE.getResultCode());
    }

    @Test(expected = NullPointerException.class)
    public void setResultCodeThrowsNullPointer() {
        abstractResponseWriterWithNull.setResultCode(TEST_RESULT_CODE);
    }

//SetResultMessage tests

    @Test
    public void afterSetResultMessageIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.setResultMessage(TEST_RESULT_MESSAGE);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void setResultMessageSetsResultMessageInUnderlyingResponse() {
        abstractResponseWriterImpl.setResultMessage(TEST_RESULT_MESSAGE);

        verify(responseMock).setResultMessage(TEST_RESULT_MESSAGE);
    }

    @Test(expected = NullPointerException.class)
    public void setResultMessageThrowsNullPointer() {
        abstractResponseWriterWithNull.setResultMessage(TEST_RESULT_MESSAGE);
    }

//SetDateTime tests

    @Test
    public void afterSetDateTimeIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.setDateTime(TEST_LOCAL_DATE_TIME);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void setDateTimeSetsDateTimeInUnderlyingResponse() {
        abstractResponseWriterImpl.setDateTime(TEST_LOCAL_DATE_TIME);

        verify(responseMock).setDateTime(TEST_LOCAL_DATE_TIME);
    }

    @Test(expected = NullPointerException.class)
    public void setDateTimeThrowsNullPointer() {
        abstractResponseWriterWithNull.setDateTime(TEST_LOCAL_DATE_TIME);
    }

//PutParameter tests

    @Test
    public void afterPutParameterIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void putParameterPutsParameterInUnderlyingResponseMap() {
        abstractResponseWriterImpl.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseMock).getParameters();
        verify(mockedMap).put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseMock, times(0)).setParameters(any());
    }

    @Test
    public void putParameterCreatesUnderlyingParametersMapIfNotExistent() {
        when(responseMock.getParameters()).thenReturn(null);

        abstractResponseWriterImpl.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        verify(responseMock).getParameters();
        verify(responseMock).setParameters(any());
    }

    @Test(expected = NullPointerException.class)
    public void putParameterThrowsNullPointer() {
        abstractResponseWriterWithNull.putParameter(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
    }

//SetParameters tests

    @Test
    public void afterSetParametersIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.setParameters(mockedMap);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void setParametersSetsTheParametersInUnderlyingResponse() {
        abstractResponseWriterImpl.setParameters(Collections.EMPTY_MAP);

        verify(responseMock).setParameters(Collections.EMPTY_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void setParametersThrowsNullPointer() {
        abstractResponseWriterWithNull.setParameters(mockedMap);
    }

//AddParameters tests

    @Test
    public void afterAddParametersIsEmptyIsFalse() {
        abstractResponseWriterImpl.setResponseIsActuallyEmpty();

        abstractResponseWriterImpl.addParameters(Collections.EMPTY_MAP);

        assertFalse(abstractResponseWriterImpl.isEmpty());
    }

    @Test
    public void addParametersPutsAllParametersInUnderlyingResponseMap() {
        abstractResponseWriterImpl.addParameters(Collections.EMPTY_MAP);
        verify(mockedMap).putAll(Collections.EMPTY_MAP);
        verify(responseMock, times(0)).setParameters(any());
    }

    @Test
    public void addParameterSetsParametersInUnderlyingParametersMapIfNotExist() {
        when(responseMock.getParameters()).thenReturn(null);

        abstractResponseWriterImpl.addParameters(Collections.EMPTY_MAP);
        verify(responseMock).setParameters(Collections.EMPTY_MAP);
    }

    @Test(expected = NullPointerException.class)
    public void addParametersThrowsNullPointer() {
        abstractResponseWriterWithNull.addParameters(mockedMap);
    }

//GetThis tests

    @Test
    public void getThisReturnsTheActualObject() {
        AbstractResponseWriter abstractResponseWriterImplCalledWithThis = abstractResponseWriterImpl.getThis();
        assertEquals(abstractResponseWriterImpl, abstractResponseWriterImplCalledWithThis);
    }

//ToString tests

    @Test
    public void checkToStringForResponseWriter() throws IOException {
        Response responseToSet = createRealResponse();
        abstractResponseWriterImpl = new AbstractResponseWriterImpl(responseToSet);

        String responseAsString = abstractResponseWriterImpl.toString();
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
        String s = abstractResponseWriterWithNull.toString();
        assertEquals(s, "null");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void toStringThrowsNjamsSdkRuntimeExceptionWithMockedResponse() {
        abstractResponseWriterImpl.toString();
    }

//Helper Classes

    private class AbstractResponseWriterImpl<T extends AbstractResponseWriterImpl<T>> extends AbstractResponseWriter<T> {

        protected AbstractResponseWriterImpl(Response response) {
            super(response);
        }
    }

}