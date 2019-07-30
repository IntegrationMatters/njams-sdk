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
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class ConditionWriterTemplateTest {

    private static final String PROCESS_PATH_WITH_CONFIG = "TestProcessWithConfig";
    private static final String PROCESS_PATH_WITHOUT_CONFIG = "TestProcessWithoutConfig";
    private static final String ACTIVITY_ID_WITH_CONFIG = "TestActivityWithConfig";
    private static final String ACTIVITY_ID_WITHOUT_CONFIG = "TestActivityWithoutConfig";

    private ConditionWriterTemplate conditionWriterTemplate;

    private Njams njamsMock;

    private ProcessConfiguration processConfigurationMock;

    private Map<String, ProcessConfiguration> processesMock;

    private ActivityConfiguration activityConfigurationMock;

    private Map<String, ActivityConfiguration> activitiesMock;

    @Before
    public void initialize() {
        njamsMock = mock(Njams.class);
        conditionWriterTemplate = spy(new ConditionWriterTemplateImpl(njamsMock));
        processConfigurationMock = mock(ProcessConfiguration.class);
        activityConfigurationMock = mock(ActivityConfiguration.class);
        processesMock = mock(Map.class);
        activitiesMock = mock(Map.class);
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH_WITH_CONFIG)).thenReturn(processConfigurationMock);
        when(njamsMock.getProcessesFromConfiguration()).thenReturn(processesMock);
        when(processConfigurationMock.getActivity(ACTIVITY_ID_WITH_CONFIG)).thenReturn(activityConfigurationMock);
        when(processConfigurationMock.getActivities()).thenReturn(activitiesMock);
    }

//SaveConfiguration tests

    @Test
    public void saveConfigurationSuccessfully() throws NjamsInstructionException {
        conditionWriterTemplate.saveConfiguration();
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test(expected = NjamsInstructionException.class)
    public void saveConfigurationThrowsRuntimeException() throws NjamsInstructionException {
        doThrow(mock(RuntimeException.class)).when(njamsMock).saveConfigurationFromMemoryToStorage();
        conditionWriterTemplate.saveConfiguration();
    }

//GetOrCreateProcessConfigurationFor tests

    @Test
    public void getExistingProcessConfiguration() {
        String processPath = PROCESS_PATH_WITH_CONFIG;
        ProcessConfiguration returnedProcess = conditionWriterTemplate.getOrCreateProcessConfigurationFor(processPath);
        verify(njamsMock).getProcessFromConfiguration(processPath);
        assertEquals(processConfigurationMock, returnedProcess);
    }

    @Test
    public void createNewProcessConfigurationBecauseItDoesntExistYet() {
        String processPath = PROCESS_PATH_WITHOUT_CONFIG;
        ProcessConfiguration returnedProcess = conditionWriterTemplate.getOrCreateProcessConfigurationFor(processPath);
        verify(njamsMock).getProcessFromConfiguration(processPath);
        verify(njamsMock).getProcessesFromConfiguration();
        verify(processesMock).put(eq(processPath), any());
        assertNotEquals(processConfigurationMock, returnedProcess);
    }

//GetOrCreateActivityConfigurationFromProcessFor tests

    @Test
    public void getExistingActivityConfiguration() {
        String activity = ACTIVITY_ID_WITH_CONFIG;

        ActivityConfiguration activityConfiguration = conditionWriterTemplate
                .getOrCreateActivityConfigurationFromProcessFor(processConfigurationMock, activity);
        verify(processConfigurationMock).getActivity(activity);
        assertEquals(activityConfigurationMock, activityConfiguration);
    }

    @Test
    public void createNewActivityConfigurationForGivenProcess() {
        String activity = ACTIVITY_ID_WITHOUT_CONFIG;

        ActivityConfiguration activityConfiguration = conditionWriterTemplate
                .getOrCreateActivityConfigurationFromProcessFor(processConfigurationMock, activity);
        verify(processConfigurationMock).getActivity(activity);
        verify(processConfigurationMock).getActivities();
        verify(activitiesMock).put(eq(activity), any());

        assertNotEquals(activityConfigurationMock, activityConfiguration);
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