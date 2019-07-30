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

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionFacade;
import com.im.njams.sdk.communication.instruction.control.processor.templates.ConditionWriterTemplate;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 * Todo: Split the RecordProcessor to a EngineWideRecordingProcessor and a ProcessRecordProcessor
 */
public class RecordProcessor extends ConditionWriterTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(RecordProcessor.class);

    public RecordProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected ConditionParameter[] getNeededParametersForProcessing() {
        return new ConditionParameter[0];
    }

    @Override
    protected void configureCondition(){
        handleEngineWideRecording();

        handleProcessRecording();
    }

    private void handleEngineWideRecording() {
        ConditionRequestReader conditionRequestReader = getConditionRequestReader();
        ConditionFacade clientCondition = getClientCondition();

        final String engineWideRecordingAsString = conditionRequestReader.getEngineWideRecording();

        if (StringUtils.isNotBlank(engineWideRecordingAsString)) {
            final boolean engineWideRecording = Boolean.parseBoolean(engineWideRecordingAsString);

            Njams condition = clientCondition.getCondition();
            condition.setRecordingToConfiguration(engineWideRecording);

            changeAllProcessRecordingsOfConditionTo(condition, engineWideRecording);
        }
    }

    private void changeAllProcessRecordingsOfConditionTo(Njams condition, boolean isEngineWideRecording) {
        condition.getProcessesFromConfiguration().values().forEach(p -> p.setRecording(isEngineWideRecording));
    }

    private void handleProcessRecording() {
        ConditionRequestReader conditionRequestReader = getConditionRequestReader();
        ConditionFacade clientCondition = getClientCondition();

        final String processRecordingAsString = conditionRequestReader.getProcessRecording();

        if (StringUtils.isNotBlank(processRecordingAsString)) {
            final boolean processRecording = "all".equalsIgnoreCase(processRecordingAsString);

            ProcessConfiguration processCondition = clientCondition.getOrCreateProcessCondition();

            processCondition.setRecording(processRecording);
        }
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            ConditionRequestReader conditionRequestReader = getConditionRequestReader();

            final String engineWideRecordingAsString = conditionRequestReader.getEngineWideRecording();
            if (StringUtils.isNotBlank(engineWideRecordingAsString)) {
                LOG.debug("EngineWideRecording has been set to {}", Boolean.parseBoolean(engineWideRecordingAsString));
            }

            final String processRecordingAsString = conditionRequestReader.getProcessRecording();
            if (StringUtils.isNotBlank(processRecordingAsString)) {
                LOG.debug("Recording for {} set to {}", getConditionRequestReader().getProcessPath(),
                        "all".equalsIgnoreCase(processRecordingAsString));
            }
        }
    }
}
