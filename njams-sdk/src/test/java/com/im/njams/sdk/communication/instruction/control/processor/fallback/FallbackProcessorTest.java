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

import com.im.njams.sdk.communication.instruction.util.InstructionWrapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class FallbackProcessorTest {

    private static final String NULL_INSTRUCTION_COMMAND = "Irce";
    private static final String NULL_REQUEST_COMMAND = "iRce";
    private static final String NULL_COMMAND_COMMAND = "irCe";
    private static final String EMPTY_COMMAND_COMMAND = "ircE";
    private static final String UNKNOWN_COMMAND_COMMAND = "irce";

    private FallbackProcessor fallbackProcessor;

    private InstructionWrapper instructionWrapperMock;

    @Before
    public void initialize() {
        fallbackProcessor = spy(new FallbackProcessor());
        instructionWrapperMock = mock(InstructionWrapper.class);
        doReturn(instructionWrapperMock).when(fallbackProcessor).getInstructionWrapper();
    }

//AfterInit

    @Test
    public void afterInit(){
        assertEquals(InstructionWrapper.EMPTY_STRING, fallbackProcessor.warningMessage);
    }

//PrepareProcessing tests

    @Test
    public void prepareProcessingClearsOldWarningMessage() {
        String oldMessage = fallbackProcessor.warningMessage = "Message_To_Delete";
        fallbackProcessor.prepareProcessing(null);
        assertNotEquals(oldMessage, fallbackProcessor.warningMessage);
    }

//Process tests

    @Test
    public void processNullInstruction() {
        mockInstructionWrapperMethods(NULL_INSTRUCTION_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.INSTRUCTION_IS_NULL.getMessage());
    }

    @Test
    public void processNullRequest() {
        mockInstructionWrapperMethods(NULL_REQUEST_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.REQUEST_IS_NULL.getMessage());
    }

    @Test
    public void processNullCommand() {
        mockInstructionWrapperMethods(NULL_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_NULL.getMessage());
    }

    @Test
    public void processEmptyCommand() {
        mockInstructionWrapperMethods(EMPTY_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_EMPTY.getMessage());
    }

    @Test
    public void processUnknownCommand() {
        mockInstructionWrapperMethods(UNKNOWN_COMMAND_COMMAND);
        when(instructionWrapperMock.getCommandOrEmptyString()).thenReturn(UNKNOWN_COMMAND_COMMAND);
        fallbackProcessor.process();
        checkWarningMessage(FallbackProcessor.InstructionProblem.COMMAND_IS_UNKNOWN.getMessage() + UNKNOWN_COMMAND_COMMAND);
    }

    private void mockInstructionWrapperMethods(String trueOrFalseString) {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        when(instructionWrapperMock.isInstructionNull()).thenReturn(parseCharToBoolean(trueOrFalseChars[0]));
        when(instructionWrapperMock.isRequestNull()).thenReturn(parseCharToBoolean(trueOrFalseChars[1]));
        when(instructionWrapperMock.isCommandNull()).thenReturn(parseCharToBoolean(trueOrFalseChars[2]));
        when(instructionWrapperMock.isCommandEmpty()).thenReturn(parseCharToBoolean(trueOrFalseChars[3]));

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

//setInstructionResponse

    @Test
    public void responseCanBeSetIfInstructionIsNotNull() {
        setResponse(false, times(1));
    }

    @Test
    public void responseCantBeSetIfInstructionIsNull() {
        setResponse(true, times(0));
    }

    private void setResponse(boolean isInstructionNull,
            VerificationMode howManyTimesHasResponseBeSet) {

        when(instructionWrapperMock.isInstructionNull()).thenReturn(isInstructionNull);
        fallbackProcessor.setInstructionResponse();
        verify(instructionWrapperMock, howManyTimesHasResponseBeSet).createResponseForInstruction(isA(Integer.class), any());
    }
}