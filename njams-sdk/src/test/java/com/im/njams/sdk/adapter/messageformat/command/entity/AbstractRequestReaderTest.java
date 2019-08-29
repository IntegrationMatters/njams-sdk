/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.im.njams.sdk.adapter.messageformat.command.entity;

import com.faizsiegeln.njams.messageformat.v4.command.Request;

import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.im.njams.sdk.api.adapter.messageformat.command.Instruction.RequestReader.EMPTY_STRING;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbstractRequestReaderTest {

    private static final String TEST_COMMAND = "command";

    private static final String EMPTY_COMMAND = "";

    private static final String TEST_PARAMETER_KEY = "key";

    private static final String TEST_PARAMETER_VALUE = "value";

    private static final String KEY_WITHOUT_VALUE = "noKey";

    private static final LocalDateTime TEST_LOCAL_DATE_TIME = DateTimeUtility.now();

    private static final String TEST_PLUGIN = "plugin";

    private Request requestMock;

    private Map<String, String> map;

    private AbstractRequestReaderImpl abstractRequestReaderImpl;

    private AbstractRequestReaderImpl abstractRequestReaderWithNull;

    @Before
    public void initialize() {
        requestMock = mock(Request.class);
        map = new HashMap<>();
        map.put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        when(requestMock.getParameters()).thenReturn(map);
        abstractRequestReaderImpl = spy(new AbstractRequestReaderImpl(requestMock));
        abstractRequestReaderWithNull = spy(new AbstractRequestReaderImpl(null));
    }

//IsEmpty tests

    @Test
    public void requestIsNull() {
        assertTrue(abstractRequestReaderWithNull.isEmpty());
    }

    @Test
    public void requestIsNotNull() {
        assertFalse(abstractRequestReaderImpl.isEmpty());
    }

//IsCommandNull tests

    @Test
    public void isCommandNullIfRequestIsNull() {
        assertTrue(abstractRequestReaderWithNull.isCommandNull());
    }

    @Test
    public void isCommandNull() {
        assertTrue(abstractRequestReaderImpl.isCommandNull());
    }

    @Test
    public void isCommandNotNull() {
        applyTestCommand();
        assertFalse(abstractRequestReaderImpl.isCommandNull());
    }

    private void applyTestCommand() {
        when(requestMock.getCommand()).thenReturn(TEST_COMMAND);
    }

//IsCommandEmpty tests

    @Test
    public void isCommandEmptyIfRequestIsNull() {
        assertTrue(abstractRequestReaderWithNull.isCommandEmpty());
    }

    @Test
    public void isCommandEmptyIfCommandIsNull() {
        assertTrue(abstractRequestReaderImpl.isCommandEmpty());
    }

    @Test
    public void isCommandEmpty() {
        applyEmptyTestCommand();
        assertTrue(abstractRequestReaderImpl.isCommandEmpty());
    }

    @Test
    public void isCommandNotEmpty() {
        applyTestCommand();
        assertFalse(abstractRequestReaderImpl.isCommandEmpty());
    }

    private void applyEmptyTestCommand() {
        when(requestMock.getCommand()).thenReturn(EMPTY_STRING);
    }

//GetCommand tests

    @Test
    public void returnsEmptyStringIfRequestIsNull(){
        assertEquals(EMPTY_COMMAND, abstractRequestReaderWithNull.getCommand());
    }

    @Test
    public void returnsEmptyStringIfCommandIsNull(){
        assertEquals(EMPTY_COMMAND, abstractRequestReaderImpl.getCommand());
    }

    @Test
    public void returnsEmptyStringIfCommandIsEmpty(){
        applyEmptyTestCommand();
        assertEquals(EMPTY_COMMAND, abstractRequestReaderImpl.getCommand());
    }

    @Test
    public void returnsUnderlyingCommand(){
        applyTestCommand();
        assertEquals(TEST_COMMAND, abstractRequestReaderImpl.getCommand());
    }

//GetParameters tests

    @Test(expected = UnsupportedOperationException.class)
    public void returnAnEmptyUnmodifiableMapIfRequestIsNull(){
        Map<String, String> parameters = abstractRequestReaderWithNull.getParameters();
        assertTrue(parameters.isEmpty());
        parameters.put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void returnsUnmodifiableRequestMap(){
        Map<String, String> parameters = abstractRequestReaderImpl.getParameters();
        assertEquals(TEST_PARAMETER_VALUE, parameters.get(TEST_PARAMETER_KEY));
        parameters.put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
    }

    @Test
    public void returnsUnderlyingRequestsMap(){
        Map<String, String> parameters = abstractRequestReaderImpl.getParameters();
        assertFalse(parameters.isEmpty());
        assertTrue(parameters.containsKey(TEST_PARAMETER_KEY));
        assertEquals(TEST_PARAMETER_VALUE, parameters.get(TEST_PARAMETER_KEY));
    }

//GetParameter tests

    @Test
    public void returnsNullForNullRequest(){
        assertNull(abstractRequestReaderWithNull.getParameter(TEST_PARAMETER_KEY));
    }

    @Test
    public void returnsNullForNullKey() {
        assertNull(abstractRequestReaderImpl.getParameter(null));
    }

    @Test
    public void returnsNullForWrongKey() {
        assertNull(abstractRequestReaderImpl.getParameter(KEY_WITHOUT_VALUE));
    }

    @Test
    public void returnsValueForCorrectKey(){
        String value = abstractRequestReaderImpl.getParameter(TEST_PARAMETER_KEY);
        assertNotNull(value);
        assertEquals(TEST_PARAMETER_VALUE, value);
    }

//ToString tests

    @Test
    public void checkToStringForResponseWriter() throws IOException {
        Request requestToSet = createRealRequest();
        abstractRequestReaderImpl = new AbstractRequestReaderImpl(requestToSet);

        String requestAsString = abstractRequestReaderImpl.toString();
        System.out.println(requestAsString);
        assertToStringCreatesSerializableOutput(requestToSet, requestAsString);
    }

    private Request createRealRequest() {
        Request request = new Request();
        request.setCommand(TEST_COMMAND);
        request.setDateTime(TEST_LOCAL_DATE_TIME);
        request.setPlugin(TEST_PLUGIN);
        request.getParameters().put(TEST_PARAMETER_KEY, TEST_PARAMETER_VALUE);
        return request;
    }

    private void assertToStringCreatesSerializableOutput(Request requestThatWasSerialized, String serializedRequest)
            throws IOException {
        Request parsedRequest = JsonUtils.parse(serializedRequest, Request.class);
        assertNotEquals(requestThatWasSerialized, parsedRequest);
        assertEquals(requestThatWasSerialized.getCommand(), parsedRequest.getCommand());
        assertEquals(requestThatWasSerialized.getPlugin(), parsedRequest.getPlugin());
        assertEquals(requestThatWasSerialized.getDateTime(), parsedRequest.getDateTime());
        assertTrue(requestThatWasSerialized.getParameters().containsKey(TEST_PARAMETER_KEY));
        assertTrue(parsedRequest.getParameters().containsKey(TEST_PARAMETER_KEY));
        assertEquals(requestThatWasSerialized.getParameters().get(TEST_PARAMETER_KEY),
                parsedRequest.getParameters().get(TEST_PARAMETER_KEY));
    }

    @Test
    public void toStringReturnsEmptyJsonForNullResponse() {
        String s = abstractRequestReaderWithNull.toString();
        assertEquals(s, "null");
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void toStringThrowsNjamsSdkRuntimeExceptionWithMockedResponse() {
        abstractRequestReaderImpl.toString();
    }

//Private helper class

    private class AbstractRequestReaderImpl extends AbstractRequestReader {
        protected AbstractRequestReaderImpl(Request requestToRead) {
            super(requestToRead);
        }
    }
}