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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.instruction.control.processors;

import com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.defaults.DefaultResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.ResultCode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class FallbackProcessorTest {

    private static final String NULL_INSTRUCTION_STATE = "Irce";
    private static final String NULL_REQUEST_STATE = "iRce";
    private static final String NULL_COMMAND_STATE = "irCe";
    private static final String EMPTY_COMMAND_STATE = "ircE";
    private static final String UNKNOWN_COMMAND_STATE = "irce";

    private FallbackProcessor fallbackProcessor;

    private DefaultInstruction defaultInstructionMock;

    private DefaultRequestReader defaultRequestReaderMock;

    private DefaultResponseWriter defaultResponseWriterMock;

    @Before
    public void initialize() {
        fallbackProcessor = spy(new FallbackProcessor());
        defaultInstructionMock = mock(DefaultInstruction.class);
        defaultRequestReaderMock = mock(DefaultRequestReader.class);
        defaultResponseWriterMock = mock(DefaultResponseWriter.class);

        doReturn(defaultInstructionMock).when(fallbackProcessor).getInstruction();
        doReturn(defaultRequestReaderMock).when(fallbackProcessor).getDefaultRequestReader();
        doReturn(defaultResponseWriterMock).when(fallbackProcessor).getDefaultResponseWriter();
    }

//AfterInit

    @Test
    public void afterInit() {
        assertEquals(DefaultRequestReader.EMPTY_STRING, fallbackProcessor.warningMessage);
    }

//ProcessDefaultInstruction tests

    @Test
    public void processDefaultInstuctionWithNullInstruction() {
        mockInstructionMethods(NULL_INSTRUCTION_STATE);
        fallbackProcessor.processDefaultInstruction();
        checkWarningMessage(FallbackProcessor.InstructionProblem.INSTRUCTION_IS_NULL.getMessage());
    }

    private void mockInstructionMethods(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        when(defaultInstructionMock.isEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[0]));
        when(defaultRequestReaderMock.isEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[1]));
        when(defaultRequestReaderMock.isCommandNull()).thenReturn(parseCharToBoolean(trueOrFalseChars[2]));
        when(defaultRequestReaderMock.isCommandEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[3]));

    }

    private boolean parseCharToBoolean(char booleanCharacter) {
        if (Character.isLowerCase(booleanCharacter)) {
            return false;
        } else {
            return true;
        }
    }

    private void checkWarningMessage(String expectedMessage) {
        assertEquals(expectedMessage, fallbackProcessor.warningMessage);
    }

    @Test
    public void processDefaultInstuctionWithNullRequest() {
        mockInstructionMethods(NULL_REQUEST_STATE);
        fallbackProcessor.processDefaultInstruction();
        checkWarningMessage(FallbackProcessor.InstructionProblem.REQUEST_IS_NULL.getMessage());
    }

    @Test
    public void processDefaultInstuctionWithNullCommand() {
        mockInstructionMethods(NULL_COMMAND_STATE);
        fallbackProcessor.processDefaultInstruction();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_NULL.getMessage());
    }

    @Test
    public void processDefaultInstuctionWithEmptyCommand() {
        mockInstructionMethods(EMPTY_COMMAND_STATE);
        fallbackProcessor.processDefaultInstruction();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_EMPTY.getMessage());
    }

    @Test
    public void processDefaultInstuctionWithUnknownCommand() {
        mockInstructionMethods(UNKNOWN_COMMAND_STATE);
        when(defaultRequestReaderMock.getCommand()).thenReturn(UNKNOWN_COMMAND_STATE);
        fallbackProcessor.processDefaultInstruction();
        checkWarningMessage(
                FallbackProcessor.InstructionProblem.COMMAND_IS_UNKNOWN.getMessage() + UNKNOWN_COMMAND_STATE);
    }

//setInstructionResponse

    @Test
    public void responseCanBeSetIfInstructionIsNotNull() {
        setResponse(false, times(1));
    }

    @Test
    public void responseCantBeSetIfInstructionIsNull() {
        setResponse(true, times(0));
    }

    private void setResponse(boolean isInstructionNull, VerificationMode howManyTimesHasResponseBeSet) {
        when(defaultInstructionMock.isEmpty()).thenReturn(isInstructionNull);
        fallbackProcessor.setInstructionResponse();
        verify(defaultResponseWriterMock, howManyTimesHasResponseBeSet).setResultCodeAndResultMessage(
                ResultCode.WARNING, fallbackProcessor.warningMessage);
    }
}