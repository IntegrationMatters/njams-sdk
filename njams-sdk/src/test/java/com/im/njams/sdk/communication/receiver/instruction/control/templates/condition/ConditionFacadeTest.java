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

package com.im.njams.sdk.communication.receiver.instruction.control.templates.condition;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.receiver.instruction.control.templates.condition.ConditionProxy;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class ConditionFacadeTest {

    private static final String PROCESS_PATH = "TestPath";
    private static final String ACTIVITY_ID = "TestActivity";

    private static final String PROCESS_EXCEPTION_MESSAGE = "Condition of process \"" + PROCESS_PATH + "\" not found";

    private static final String ACTIVITY_EXCEPTION_MESSAGE =
            "Condition of activity \"" + ACTIVITY_ID + "\" " + "on process \"" + PROCESS_PATH + "\" not found";

    private static final String EXTRACT_EXCEPTION_MESSAGE =
            "Extract for activity \"" + ACTIVITY_ID + "\" on process \"" + PROCESS_PATH + "\" not found";

    private ConditionProxy conditionFacade;

    private Njams njamsMock;

    private Map<String, ProcessConfiguration> processesMock;

    private Map<String, ActivityConfiguration> activitiesMock;

    private ProcessConfiguration processConditionMock;

    private ActivityConfiguration activityConditionMock;

    @Before
    public void initialize() {
        njamsMock = mock(Njams.class);
        conditionFacade = spy(new ConditionProxy(njamsMock));
        conditionFacade.setProcessPath(PROCESS_PATH);
        conditionFacade.setActivityId(ACTIVITY_ID);
        processesMock = mock(Map.class);
        activitiesMock = mock(Map.class);
        processConditionMock = mock(ProcessConfiguration.class);
        activityConditionMock = mock(ActivityConfiguration.class);

        when(njamsMock.getProcessesFromConfiguration()).thenReturn(processesMock);
        when(processConditionMock.getActivities()).thenReturn(activitiesMock);
    }

//GetProcessCondition tests

    @Test(expected = NjamsInstructionException.class)
    public void NoProcessConditionFound() throws NjamsInstructionException {
        try {
            conditionFacade.getProcessCondition();
        } catch (NjamsInstructionException ex) {
            assertEquals(PROCESS_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void foundProcessCondition() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        ProcessConfiguration returnedProcessCondition = conditionFacade.getProcessCondition();
        assertEquals(processConditionMock, returnedProcessCondition);
    }

//GetActivityCondition tests

    @Test(expected = NjamsInstructionException.class)
    public void NoProcessConditionFoundWhileSearchingForActivityCondition() throws NjamsInstructionException {
        try {
            conditionFacade.getActivityCondition();
        } catch (NjamsInstructionException ex) {
            assertEquals(PROCESS_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = NjamsInstructionException.class)
    public void NoActivityConditionFound() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        try {
            conditionFacade.getActivityCondition();
        } catch (NjamsInstructionException ex) {
            assertEquals(ACTIVITY_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void foundActivityCondition() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        when(processConditionMock.getActivity(ACTIVITY_ID)).thenReturn(activityConditionMock);

        ActivityConfiguration returnedActivityCondition = conditionFacade.getActivityCondition();

        assertEquals(activityConditionMock, returnedActivityCondition);
    }

//GetExtract tests

    @Test(expected = NjamsInstructionException.class)
    public void noProcessConditionFoundWhileGetExtract() throws NjamsInstructionException {
        try {
            conditionFacade.getExtract();
        } catch (NjamsInstructionException ex) {
            assertEquals(PROCESS_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = NjamsInstructionException.class)
    public void noActivityConditionFoundWhileGetExtract() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        try {
            conditionFacade.getExtract();
        } catch (NjamsInstructionException ex) {
            assertEquals(ACTIVITY_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = NjamsInstructionException.class)
    public void noExtractFound() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        when(processConditionMock.getActivity(ACTIVITY_ID)).thenReturn(activityConditionMock);
        try {
            conditionFacade.getExtract();
        } catch (NjamsInstructionException ex) {
            assertEquals(EXTRACT_EXCEPTION_MESSAGE, ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void getExisitingExtract() throws NjamsInstructionException {
        Extract extractMock = mock(Extract.class);
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        when(processConditionMock.getActivity(ACTIVITY_ID)).thenReturn(activityConditionMock);
        when(activityConditionMock.getExtract()).thenReturn(extractMock);

        Extract returnedExtract = conditionFacade.getExtract();
        assertEquals(extractMock, returnedExtract);
    }

//GetOrCreateProcessConfiguration tests

    @Test
    public void createNewProcessConfigurationBecauseItDoesntExistYet() throws NjamsInstructionException {
        ProcessConfiguration returnedProcessConfiguration = conditionFacade.getOrCreateProcessCondition();
        verify(njamsMock).getProcessesFromConfiguration();
        verify(processesMock).put(eq(PROCESS_PATH), any());
        assertNotEquals(processConditionMock, returnedProcessConfiguration);
    }

    @Test
    public void getExistingProcessConfiguration() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        ProcessConfiguration returnedProcessConfiguration = conditionFacade.getOrCreateProcessCondition();
        assertEquals(processConditionMock, returnedProcessConfiguration);
    }

//GetOrCreateActivityConfiguration tests

    @Test
    public void createNewActivityConfigurationForGivenProcess() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        ActivityConfiguration returnedActivityConfiguration = conditionFacade.getOrCreateActivityCondition();
        verify(processConditionMock).getActivities();
        verify(activitiesMock).put(eq(ACTIVITY_ID), any());
        assertNotEquals(processConditionMock, returnedActivityConfiguration);
    }

    @Test
    public void getExistingActivityConfiguration() throws NjamsInstructionException {
        when(njamsMock.getProcessFromConfiguration(PROCESS_PATH)).thenReturn(processConditionMock);
        when(processConditionMock.getActivity(ACTIVITY_ID)).thenReturn(activityConditionMock);
        ActivityConfiguration returnedActivityConfiguration = conditionFacade.getOrCreateActivityCondition();
        assertEquals(activityConditionMock, returnedActivityConfiguration);
    }

//SaveConfiguration tests

    @Test
    public void saveConfigurationSuccessfully() throws NjamsInstructionException {
        conditionFacade.saveCondition();
        verify(njamsMock).saveConfigurationFromMemoryToStorage();
    }

    @Test(expected = NjamsInstructionException.class)
    public void saveConfigurationThrowsRuntimeException() throws NjamsInstructionException {
        doThrow(mock(RuntimeException.class)).when(njamsMock).saveConfigurationFromMemoryToStorage();
        conditionFacade.saveCondition();
    }
}