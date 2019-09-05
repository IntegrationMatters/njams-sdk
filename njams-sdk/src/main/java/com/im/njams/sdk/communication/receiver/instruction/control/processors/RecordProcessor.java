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
package com.im.njams.sdk.communication.receiver.instruction.control.processors;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.condition.ConditionRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.NjamsInstructionException;
import com.im.njams.sdk.communication.receiver.instruction.control.processors.templates.condition.ConditionProcessorTemplate;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 * Todo: Split the RecordProcessor to a EngineWideRecordingProcessor and a ProcessRecordProcessor
 */
public class RecordProcessor extends ConditionProcessorTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(RecordProcessor.class);

    private boolean isEngineWideRecording;

    private boolean isProcessRecording;

    public RecordProcessor(Njams njams) {
        super(njams);
    }

    @Override
    public String getCommandToListenTo(){
        return Command.RECORD.commandString();
    }

    @Override
    protected String[] getEssentialParametersForProcessing() {
        return NO_ESSENTIAL_PARAMETERS;
    }

    @Override
    protected void processConditionInstruction() throws NjamsInstructionException {
        handleEngineWideRecording();

        handleProcessRecording();

        saveCondition();
    }

    private void handleEngineWideRecording() {

        final String engineWideRecordingAsString = getRequestReader().getEngineWideRecording();

        if (StringUtils.isNotBlank(engineWideRecordingAsString)) {
            isEngineWideRecording = Boolean.parseBoolean(engineWideRecordingAsString);

            setEngineWideRecording();
        }
    }

    private void setEngineWideRecording() {
        Njams condition = conditionProxy.getCondition();

        condition.setRecordingToConfiguration(isEngineWideRecording);

        changeAllProcessRecordingsOfConditionTo(condition);
    }

    private void changeAllProcessRecordingsOfConditionTo(Njams condition) {
        condition.getProcessesFromConfiguration().values().forEach(p -> p.setRecording(isEngineWideRecording));
    }

    private void handleProcessRecording() {
        final String processRecordingAsString = getRequestReader().getProcessRecording();

        if (StringUtils.isNotBlank(processRecordingAsString)) {
            isProcessRecording = "all".equalsIgnoreCase(processRecordingAsString);

            ProcessConfiguration processCondition = conditionProxy.getOrCreateProcessCondition();

            processCondition.setRecording(isProcessRecording);
        }
    }

    @Override
    protected void logProcessingSuccess() {
        if (LOG.isDebugEnabled()) {
            ConditionRequestReader conditionRequestReader = getRequestReader();
            if (StringUtils.isNotBlank(conditionRequestReader.getEngineWideRecording())) {
                LOG.debug("EngineWideRecording has been set to {}", isEngineWideRecording);
            }

            if (StringUtils.isNotBlank(conditionRequestReader.getProcessRecording())) {
                LOG.debug("Recording for {} set to {}", conditionRequestReader.getProcessPath(), isProcessRecording);
            }
        }
    }
}
