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
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ConditionWriterTemplateTest {

    private ConditionWriterTemplate conditionWriterTemplate;

    private Njams njamsMock;

    private ConditionFacade conditionFacadeMock;

    @Before
    public void initialize() {
        njamsMock = mock(Njams.class);
        conditionWriterTemplate = spy(new ConditionWriterTemplateImpl(njamsMock));

        conditionFacadeMock = mock(ConditionFacade.class);
        doReturn(conditionFacadeMock).when(conditionWriterTemplate).getClientCondition();
    }

//ProcessConditionInstruction tests

    @Test
    public void processConditionInstruction() throws NjamsInstructionException {
        conditionWriterTemplate.processConditionInstruction();

        verify(conditionWriterTemplate).configureCondition();
        verify(conditionFacadeMock).saveCondition();
    }

//Private helper classes

    private class ConditionWriterTemplateImpl extends ConditionWriterTemplate {

        public ConditionWriterTemplateImpl(Njams njams) {
            super(njams);
        }

        @Override
        protected ConditionParameter[] getNeededParametersForProcessing() {
            return new ConditionParameter[0];
        }

        @Override
        protected void logProcessingSuccess() {

        }

        @Override
        protected void configureCondition() throws NjamsInstructionException {

        }
    }
}