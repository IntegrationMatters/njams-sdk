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
package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.exceptions.NjamsInstructionException;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ConfigureExtractProcessorTest {

    private static final String PROCESS_PATH = "TestProcessPath";
    private static final String ACTIVITY_ID = "TestActivityId";
    private static String CORRECT_EXTRACT_JSON;

    static {
        try {
            CORRECT_EXTRACT_JSON = JsonUtils.serialize(new Extract());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private static final String NO_CORRECT_EXTRACT_JSON = "FailedExtract";

    private ConfigureExtractProcessor configureExtractProcessor;

    private Njams njamsMock;

    private ConditionRequestReader readerMock;

    private ProcessConfiguration processConfigurationMock;

    private ActivityConfiguration activityConfigurationMock;

    @Before
    public void setNewProcessor() {
        njamsMock = mock(Njams.class);
        configureExtractProcessor = spy(new ConfigureExtractProcessor(njamsMock));
        readerMock = mock(ConditionRequestReader.class);
        processConfigurationMock = mock(ProcessConfiguration.class);
        activityConfigurationMock = mock(ActivityConfiguration.class);

        doReturn(readerMock).when(configureExtractProcessor).getConditionRequestReader();
        when(readerMock.getProcessPath()).thenReturn(PROCESS_PATH);
        when(readerMock.getActivityId()).thenReturn(ACTIVITY_ID);

        doReturn(processConfigurationMock).when(configureExtractProcessor)
                .getOrCreateProcessConfigurationFor(PROCESS_PATH);
        doReturn(activityConfigurationMock).when(configureExtractProcessor)
                .getOrCreateActivityConfigurationFromProcessFor(processConfigurationMock, ACTIVITY_ID);
    }

//GetNeededParametersForProcessing tests

    @Test
    public void getNeededParametersForProcessingTest() {
        List<ConditionParameter> neededParametersAsList = Arrays
                .asList(configureExtractProcessor.getNeededParametersForProcessing());
        assertTrue(neededParametersAsList.contains(ConditionParameter.PROCESS_PATH));
        assertTrue(neededParametersAsList.contains(ConditionParameter.ACTIVITY_ID));
        assertTrue(neededParametersAsList.contains(ConditionParameter.EXTRACT));
    }

//ConfigureCondition tests

    @Test
    public void configureConditionWorks() throws NjamsInstructionException {
        when(readerMock.getExtract()).thenReturn(CORRECT_EXTRACT_JSON);
        configureExtractProcessor.configureCondition();
        verify(configureExtractProcessor).getOrCreateProcessConfigurationFor(PROCESS_PATH);
        verify(configureExtractProcessor).getOrCreateActivityConfigurationFromProcessFor(processConfigurationMock, ACTIVITY_ID);
        verify(activityConfigurationMock).setExtract(any());
    }

    @Test(expected = NjamsInstructionException.class)
    public void configureConditionExtractIsNotARealExtract() throws NjamsInstructionException {
        when(readerMock.getExtract()).thenReturn(NO_CORRECT_EXTRACT_JSON);
        configureExtractProcessor.configureCondition();
        verify(configureExtractProcessor).getOrCreateProcessConfigurationFor(PROCESS_PATH);
        verify(configureExtractProcessor).getOrCreateActivityConfigurationFromProcessFor(processConfigurationMock, ACTIVITY_ID);
    }

//LogProcessingSuccess test

    @Test
    public void logProcessingTest(){
        configureExtractProcessor.logProcessingSuccess();
        verify(readerMock).getProcessPath();
        verify(readerMock).getActivityId();
    }
}