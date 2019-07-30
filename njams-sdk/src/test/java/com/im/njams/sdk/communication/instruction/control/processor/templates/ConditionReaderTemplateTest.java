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

package com.im.njams.sdk.communication.instruction.control.processor.templates;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionInstruction;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionReaderTemplate.DEFAULT_SUCCESS_MESSAGE;
import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ConditionReaderTemplateTest {

    private static final String NJAMS_INSTRUCTION_EXCEPTION_MESSAGE = "Njams-Exception-message";

    private ConditionReaderTemplate conditionReaderTemplate;

    private Njams njamsMock;

    private ConditionInstruction conditionInstructionMock;

    private ConditionRequestReader conditionRequestReaderMock;

    private ConditionResponseWriter conditionResponseWriterMock;

    private NjamsInstructionException njamsInstructionExceptionMock;

    private List<String> missingParameterMock = mock(List.class);

    @Before
    public void initialize() {
        njamsMock = mock(Njams.class);
        conditionReaderTemplate = spy(new ConditionReaderTemplateImpl(njamsMock));
        conditionInstructionMock = mock(ConditionInstruction.class);
        conditionRequestReaderMock = mock(ConditionRequestReader.class);
        conditionResponseWriterMock = mock(ConditionResponseWriter.class);
        njamsInstructionExceptionMock = mock(NjamsInstructionException.class);

        doReturn(conditionInstructionMock).when(conditionReaderTemplate).getInstruction();
        when(conditionInstructionMock.getRequestReader()).thenReturn(conditionRequestReaderMock);
        when(conditionInstructionMock.getResponseWriter()).thenReturn(conditionResponseWriterMock);
        when(njamsInstructionExceptionMock.getMessage()).thenReturn(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE);
    }

//Process tests

    private static final String NEEDED_PARAMETERS_WERENT_SET = "wp";
    private static final String PROCESSING_INSTRUCTION_SUCCESS = "Wp";
    private static final String PROCESSING_INSTRUCTION_EXCEPTION = "WP";

    @Test
    public void processNeededParametersWerentSet() throws NjamsInstructionException {
        process(NEEDED_PARAMETERS_WERENT_SET);
    }

    @Test
    public void processInstructionSuccessfully() throws NjamsInstructionException {
        process(PROCESSING_INSTRUCTION_SUCCESS);
    }

    @Test
    public void processThrowsExceptionWhileProcessingInstruction() throws NjamsInstructionException {
        process(PROCESSING_INSTRUCTION_EXCEPTION);
    }

    private void process(String transitionWord) throws NjamsInstructionException {
        mockProcessMethods(transitionWord);

        conditionReaderTemplate.process();

        verifyCorrectProcessing(transitionWord);
    }

    private void mockProcessMethods(String trueOrFalseString) throws NjamsInstructionException {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        doNothing().when(conditionReaderTemplate).setDefaultSuccessResponse();
        doNothing().when(conditionReaderTemplate).setWarningResponse(any());
        doNothing().when(conditionReaderTemplate).logProcessingThrewException(any());

        doReturn(parseCharToBoolean(trueOrFalseChars[0])).when(conditionReaderTemplate)
                .wereAllNeededRequestParametersSet(any());
        if (parseCharToBoolean(trueOrFalseChars[1])) {
            doThrow(njamsInstructionExceptionMock).when(conditionReaderTemplate).processConditionInstruction();
        }
    }

    private boolean parseCharToBoolean(char booleanCharacter) {
        if (Character.isLowerCase(booleanCharacter)) {
            return false;
        } else {
            return true;
        }
    }

    private void verifyCorrectProcessing(String trueOrFalseString) throws NjamsInstructionException {
        char[] trueOrFalseChars = trueOrFalseString.toCharArray();

        verify(conditionReaderTemplate).fillMissingParametersList();

        if (parseCharToBoolean(trueOrFalseChars[0])) {
            verify(conditionReaderTemplate).processConditionInstruction();
            if (!parseCharToBoolean(trueOrFalseChars[1])) {
                verify(conditionReaderTemplate).setDefaultSuccessResponse();
                verify(conditionReaderTemplate).logProcessingSuccess();
                verify(conditionReaderTemplate, times(0)).getProcessingDidntWorkMessage(njamsInstructionExceptionMock);
                verify(conditionReaderTemplate, times(0)).setWarningResponse(any());
                verify(conditionReaderTemplate, times(0)).logProcessingThrewException(njamsInstructionExceptionMock);
            } else {
                verify(conditionReaderTemplate, times(0)).setDefaultSuccessResponse();
                verify(conditionReaderTemplate, times(0)).logProcessingSuccess();
                verify(conditionReaderTemplate).getProcessingDidntWorkMessage(njamsInstructionExceptionMock);
                verify(conditionReaderTemplate).setWarningResponse(any());
                verify(conditionReaderTemplate).logProcessingThrewException(njamsInstructionExceptionMock);
            }
            verify(conditionReaderTemplate, times(0)).GetInvalidParametersMessage(any());
            verify(conditionReaderTemplate, times(0)).logInvalidParameters(any());
        } else {
            verify(conditionReaderTemplate, times(0)).processConditionInstruction();
            verify(conditionReaderTemplate, times(0)).setDefaultSuccessResponse();
            verify(conditionReaderTemplate, times(0)).logProcessingSuccess();
            verify(conditionReaderTemplate, times(0)).getProcessingDidntWorkMessage(any());
            verify(conditionReaderTemplate, times(0)).logProcessingThrewException(any());
            verify(conditionReaderTemplate).GetInvalidParametersMessage(any());
            verify(conditionReaderTemplate).setWarningResponse(any());
            verify(conditionReaderTemplate).logInvalidParameters(any());
        }
    }

//FillMissingParametersList tests

    @Test
    public void fillMissingParametersList() {
        ConditionParameter[] conditionParameterMock = new ConditionParameter[0];
        doReturn(conditionParameterMock).when(conditionReaderTemplate).getNeededParametersForProcessing();
        when(conditionRequestReaderMock.searchForMissingParameters(conditionParameterMock))
                .thenReturn(missingParameterMock);

        List<String> filledMissingParameters = conditionReaderTemplate.fillMissingParametersList();
        assertEquals(missingParameterMock, filledMissingParameters);
    }

//WereAllNeededRequestParametersSet tests

    @Test
    public void neededParametersAreAllAvailable() {
        checkNeededAndActualParameters(true);
    }

    @Test
    public void notAllNeededParametersWereSet() {
        checkNeededAndActualParameters(false);
    }

    private void checkNeededAndActualParameters(boolean isMissingParametersListEmpty) {
        when(missingParameterMock.isEmpty()).thenReturn(isMissingParametersListEmpty);

        boolean noMissingParameters = conditionReaderTemplate.wereAllNeededRequestParametersSet(missingParameterMock);

        assertEquals(noMissingParameters, isMissingParametersListEmpty);
    }

//SetDefaultSuccessResponse tests

    @Test
    public void setDefaultSuccessResponseWithEmptyWriter() {
        setDefaultSuccessResponse(true, 1);
    }

    @Test
    public void setNoDefaultResponseIfWriterIsNotEmpty() {
        setDefaultSuccessResponse(false, 0);
    }

    private void setDefaultSuccessResponse(boolean isWriterEmpty, int howManyTimesShouldResponseBeSet) {
        when(conditionResponseWriterMock.isEmpty()).thenReturn(isWriterEmpty);
        conditionReaderTemplate.setDefaultSuccessResponse();
        verify(conditionResponseWriterMock, times(howManyTimesShouldResponseBeSet))
                .setResultCodeAndResultMessage(ResponseWriter.ResultCode.SUCCESS, DEFAULT_SUCCESS_MESSAGE);

    }

//GetProcessingDidntWorkMessage tests

    @Test
    public void getExceptionIfProcessingDidntWorkWithoutASourceException() {
        when(njamsInstructionExceptionMock.getCause()).thenReturn(null);

        String processingDidntWorkMessage = conditionReaderTemplate
                .getProcessingDidntWorkMessage(njamsInstructionExceptionMock);

        assertEquals(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE, processingDidntWorkMessage);
    }

    @Test
    public void getExceptionIfProcessingDidntWorkWithASourceExceptionButWithANullAsMessage() {
        Exception causeExceptionMock = mock(Exception.class);
        when(causeExceptionMock.getMessage()).thenReturn(null);

        when(njamsInstructionExceptionMock.getCause()).thenReturn(causeExceptionMock);

        String processingDidntWorkMessage = conditionReaderTemplate
                .getProcessingDidntWorkMessage(njamsInstructionExceptionMock);

        assertEquals(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE, processingDidntWorkMessage);
    }

    @Test
    public void getExceptionIfProcessingDidntWorkWithASourceExceptionButWithoutAMessage() {
        final String exceptionMessage = "";
        Exception causeExceptionMock = mock(Exception.class);
        when(causeExceptionMock.getMessage()).thenReturn(exceptionMessage);

        when(njamsInstructionExceptionMock.getCause()).thenReturn(causeExceptionMock);

        String processingDidntWorkMessage = conditionReaderTemplate
                .getProcessingDidntWorkMessage(njamsInstructionExceptionMock);

        assertEquals(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE, processingDidntWorkMessage);
    }

    @Test
    public void getExceptionIfProcessingDidntWorkWithASourceException() {
        final String exceptionMessage = "Test";
        Exception causeExceptionMock = mock(Exception.class);
        when(causeExceptionMock.getMessage()).thenReturn(exceptionMessage);

        when(njamsInstructionExceptionMock.getCause()).thenReturn(causeExceptionMock);

        String processingDidntWorkMessage = conditionReaderTemplate
                .getProcessingDidntWorkMessage(njamsInstructionExceptionMock);

        assertEquals(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE + ": " + exceptionMessage, processingDidntWorkMessage);
    }

//SetWarningResponse tests

    @Test
    public void setWarningResponseTest() {
        conditionReaderTemplate.setWarningResponse(NJAMS_INSTRUCTION_EXCEPTION_MESSAGE);
        verify(conditionResponseWriterMock)
                .setResultCodeAndResultMessage(ResponseWriter.ResultCode.WARNING, NJAMS_INSTRUCTION_EXCEPTION_MESSAGE);
    }

//LogProcessingThrewException tests

    @Test
    public void verifyThatCommandProcessPathAndActivityIdWillBeUsedForProcessLoggingIfExceptionIsThrown() {
        conditionReaderTemplate.logProcessingThrewException(njamsInstructionExceptionMock);
        verify(conditionRequestReaderMock).getCommand();
        verify(conditionRequestReaderMock).getProcessPath();
        verify(conditionRequestReaderMock).getActivityId();
    }

//GetInvalidParametersMessage tests

    @Test
    public void setInvalidParameterResponseWithoutMissingParameters() {
        List<String> missingParameters = EMPTY_LIST;
        String invalidParameterMessage = conditionReaderTemplate.GetInvalidParametersMessage(missingParameters);
        assertEquals("Missing parameters: " + missingParameters.toString(), invalidParameterMessage);
    }

    @Test
    public void setInvalidParameterResponseWithOneMissingParameter() {
        List<String> missingParameters = new ArrayList<>();
        fillMissingParametersWith(missingParameters, ConditionParameter.PROCESS_PATH);
        String invalidParameterMessage = conditionReaderTemplate.GetInvalidParametersMessage(missingParameters);
        assertEquals("Missing parameter: " + missingParameters.toString(), invalidParameterMessage);
    }

    @Test
    public void setInvalidParameterResponseWithMoreThanOneMissingParameter() {
        List<String> missingParameters = new ArrayList<>();
        fillMissingParametersWith(missingParameters, ConditionParameter.PROCESS_PATH, ConditionParameter.ACTIVITY_ID);
        String invalidParameterMessage = conditionReaderTemplate.GetInvalidParametersMessage(missingParameters);
        assertEquals("Missing parameters: " + missingParameters.toString(), invalidParameterMessage);
    }

    private void fillMissingParametersWith(List<String> missingParameters, ConditionParameter... parameters) {
        Arrays.stream(parameters)
                .forEach(conditionParameter -> missingParameters.add(conditionParameter.getParamKey()));
    }

//GetClientCondition tests

    @Test
    public void getClientConditionTest() {
        assertTrue(conditionReaderTemplate.getClientCondition() instanceof Njams);
    }

//GetConditionRequestReader tests

    @Test
    public void GetConditionRequestReaderTest() {
        ConditionRequestReader conditionRequestReader = conditionReaderTemplate.getConditionRequestReader();
        verify(conditionInstructionMock).getRequestReader();
        assertTrue(conditionRequestReader instanceof ConditionRequestReader);
    }

//GetConditionResponseWriter tests

    @Test
    public void GetConditionResponseWriterTest() {
        ConditionResponseWriter conditionResponseWriter = conditionReaderTemplate.getConditionResponseWriter();
        verify(conditionInstructionMock).getResponseWriter();
        assertTrue(conditionResponseWriter instanceof ConditionResponseWriter);
    }

//Private helper classes

    private class ConditionReaderTemplateImpl extends ConditionReaderTemplate {

        public ConditionReaderTemplateImpl(Njams njams) {
            super(njams);
        }

        @Override
        protected ConditionParameter[] getNeededParametersForProcessing() {
            return new ConditionParameter[0];
        }

        @Override
        protected void processConditionInstruction() throws NjamsInstructionException {

        }

        @Override
        protected void logProcessingSuccess() {

        }
    }
}