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
package com.im.njams.sdk.communication.instruction.control.processor.fallback;

import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FallbackProcessorTest {

    private static final String NULL_INSTRUCTION_COMMAND = "Irce";
    private static final String NULL_REQUEST_COMMAND = "iRce";
    private static final String NULL_COMMAND_COMMAND = "irCe";
    private static final String EMPTY_COMMAND_COMMAND = "ircE";
    private static final String UNKNOWN_COMMAND_COMMAND = "irce";

    private FallbackProcessor fallbackProcessor;

    private DefaultInstruction instruction;

    private DefaultRequestReader reader;

    private DefaultResponseWriter writer;

    @Before
    public void initialize() {
        fallbackProcessor = spy(new FallbackProcessor());
        instruction = mock(DefaultInstruction.class);
        doReturn(instruction).when(fallbackProcessor).getInstruction();
        reader = mock(DefaultRequestReader.class);
        when(instruction.getRequestReader()).thenReturn(reader);
        writer = mock(DefaultResponseWriter.class);
        when(instruction.getResponseWriter()).thenReturn(writer);
        when(writer.setResultCode(any())).thenReturn(writer);
        when(writer.setResultMessage(any())).thenReturn(writer);
    }

//AfterInit

    @Test
    public void afterInit() {
        assertEquals(DefaultRequestReader.EMPTY_STRING, fallbackProcessor.warningMessage);
    }

//PrepareProcessing tests

    @Test
    public void prepareProcessingClearsOldWarningMessage() {
        String oldMessage = fallbackProcessor.warningMessage = "Message_To_Delete";
        boolean prepareProcessingSuccessful = fallbackProcessor.prepareProcessing();
        assertNotEquals(oldMessage, fallbackProcessor.warningMessage);
        assertTrue(prepareProcessingSuccessful);
    }

//Process tests

    @Test
    public void processNullInstruction() {
        mockInstructionMethods(NULL_INSTRUCTION_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.INSTRUCTION_IS_NULL.getMessage());
    }

    private void mockInstructionMethods(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        when(instruction.isEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[0]));
        when(reader.isEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[1]));
        when(reader.isCommandNull()).thenReturn(parseCharToBoolean(trueOrFalseChars[2]));
        when(reader.isCommandEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[3]));

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
    public void processNullRequest() {
        mockInstructionMethods(NULL_REQUEST_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.REQUEST_IS_NULL.getMessage());
    }

    @Test
    public void processNullCommand() {
        mockInstructionMethods(NULL_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_NULL.getMessage());
    }

    @Test
    public void processEmptyCommand() {
        mockInstructionMethods(EMPTY_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_EMPTY.getMessage());
    }

    @Test
    public void processUnknownCommand() {
        mockInstructionMethods(UNKNOWN_COMMAND_COMMAND);
        when(reader.getCommand()).thenReturn(UNKNOWN_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(
                FallbackProcessor.InstructionProblem.COMMAND_IS_UNKNOWN.getMessage() + UNKNOWN_COMMAND_COMMAND);
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
        when(instruction.isEmpty()).thenReturn(isInstructionNull);
        fallbackProcessor.setInstructionResponse();
        verify(writer, howManyTimesHasResponseBeSet).setResultCode(ResponseWriter.ResultCode.WARNING);
        verify(writer, howManyTimesHasResponseBeSet).setResultMessage(fallbackProcessor.warningMessage);
    }
}