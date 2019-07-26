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
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ConditionReaderTemplateTest {

    private ConditionReaderTemplate conditionReaderTemplate;

    private Njams njamsMock;

    private ConditionInstruction conditionInstructionMock;

    private ConditionRequestReader conditionRequestReaderMock;

    private ConditionResponseWriter conditionResponseWriterMock;

    private NjamsInstructionException njamsInstructionExceptionMock;

    @Before
    public void initialize(){
        njamsMock = mock(Njams.class);
        conditionReaderTemplate = spy(new ConditionReaderTemplateImpl(njamsMock));
        conditionInstructionMock = mock(ConditionInstruction.class);
        conditionRequestReaderMock = mock(ConditionRequestReader.class);
        conditionResponseWriterMock = mock(ConditionResponseWriter.class);
        njamsInstructionExceptionMock = mock(NjamsInstructionException.class);

        doReturn(conditionInstructionMock).when(conditionReaderTemplate).getInstruction();
        when(conditionInstructionMock.getRequestReader()).thenReturn(conditionRequestReaderMock);
        when(conditionInstructionMock.getResponseWriter()).thenReturn(conditionResponseWriterMock);
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
        doNothing().when(conditionReaderTemplate).setProcessingDidntWorkResponse(any());
        doNothing().when(conditionReaderTemplate).logProcessingThrewException(any());
        doNothing().when(conditionReaderTemplate).setInvalidParametersResponse();


        doReturn(parseCharToBoolean(trueOrFalseChars[0])).when(conditionReaderTemplate).wereNeededRequestParametersSet();
        if(parseCharToBoolean(trueOrFalseChars[1])){
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

        verify(conditionReaderTemplate).setDefaultSuccessResponse();
        verify(conditionReaderTemplate).wereNeededRequestParametersSet();

        if(parseCharToBoolean(trueOrFalseChars[0])){
            verify(conditionReaderTemplate).processConditionInstruction();
            if(!parseCharToBoolean(trueOrFalseChars[1])){
                verify(conditionReaderTemplate).logProcessingSuccess();
                verify(conditionReaderTemplate,times(0)).setProcessingDidntWorkResponse(njamsInstructionExceptionMock);
                verify(conditionReaderTemplate,times(0)).logProcessingThrewException(njamsInstructionExceptionMock);
            }else{
                verify(conditionReaderTemplate,times(0)).logProcessingSuccess();
                verify(conditionReaderTemplate).setProcessingDidntWorkResponse(njamsInstructionExceptionMock);
                verify(conditionReaderTemplate).logProcessingThrewException(njamsInstructionExceptionMock);
            }
            verify(conditionReaderTemplate, times(0)).setInvalidParametersResponse();
        }else{
            verify(conditionReaderTemplate, times(0)).processConditionInstruction();
            verify(conditionReaderTemplate, times(0)).logProcessingSuccess();
            verify(conditionReaderTemplate, times(0)).setProcessingDidntWorkResponse(njamsInstructionExceptionMock);
            verify(conditionReaderTemplate, times(0)).logProcessingThrewException(njamsInstructionExceptionMock);
            verify(conditionReaderTemplate).setInvalidParametersResponse();
        }
    }

//SetDefaultSuccessResponse tests
//WereNeededRequestParametersSet tests
//SetProcessingDidntWorkResponse tests
//LogProcessingThrewException tests
//SetInvalidParametersResponse tests
//GetClientCondition tests
//GetConditionRequestReader tests
//GetConditionResponseWriter tests




//Private helper classes

    private class ConditionReaderTemplateImpl extends ConditionReaderTemplate{

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