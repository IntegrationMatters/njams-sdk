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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionParameter;
import com.im.njams.sdk.adapter.messageformat.command.entity.ConditionRequestReader;
import com.im.njams.sdk.adapter.messageformat.command.entity.DefaultRequestReader;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.ResponseWriter;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class ConfigureExtractProcessor extends ConditionInstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigureExtractProcessor.class);

    private static final ConditionParameter[] neededParameter =
            new ConditionParameter[]{ConditionParameter.PROCESS_PATH, ConditionParameter.ACTIVITY_ID,
                    ConditionParameter.EXTRACT};

    private static final String UNABLE_TO_DESERIALZE_EXTRACT_MESSAGE = "Unable to deserialize extract";
    private String processPath;

    private String activityId;

    private String extractAsString;

    public ConfigureExtractProcessor(Njams njams) {
        super(njams);
    }

//    protected void oldProcessInstruction(AbstractConfigurationProcessor.InstructionSupport instructionSupport) {
//        if (!instructionSupport.validate(AbstractConfigurationProcessor.InstructionSupport.PROCESS_PATH,
//                AbstractConfigurationProcessor.InstructionSupport.ACTIVITY_ID, "extract")) {
//            return;
//        }
//        //fetch parameters
//        final String processPath = instructionSupport.getProcessPath();
//        final String activityId = instructionSupport.getActivityId();
//        final String extractString = instructionSupport.getParameter("extract");
//
//        //execute action
//        ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
//        if (process == null) {
//            process = new ProcessConfiguration();
//            njams.getProcessesFromConfiguration().put(processPath, process);
//        }
//        ActivityConfiguration activity = null;
//        activity = process.getActivity(activityId);
//        if (activity == null) {
//            activity = new ActivityConfiguration();
//            process.getActivities().put(activityId, activity);
//        }
//        Extract extract = null;
//        try {
//            final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
//            extract = mapper.readValue(extractString, Extract.class);
//        } catch (final Exception e) {
//            instructionSupport.error("Unable to deserialize extract", e);
//            return;
//        }
//        activity.setExtract(extract);
//        saveConfiguration(instructionSupport);
//        LOG.debug("Configure extract for {}", processPath);
//    }

    @Override
    protected void clear() {
        processPath = DefaultRequestReader.EMPTY_STRING;
        activityId = DefaultRequestReader.EMPTY_STRING;
        extractAsString = DefaultRequestReader.EMPTY_STRING;
    }

    @Override
    protected ConditionParameter[] getNeededParametersForProcessing() {
        return neededParameter;
    }

    @Override
    protected void process() {
        //fetch parameters
        fetchParameters();

        final ProcessConfiguration processConfiguration = getOrCreateProcessConfigurationFor(processPath);
        final ActivityConfiguration activityConfiguration = getOrCreateActivityConfigurationFromProcessFor(processConfiguration, activityId);

        //execute action

        Extract extract = null;
        try {
            final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
            extract = mapper.readValue(extractAsString, Extract.class);
        } catch (final Exception e) {
            getInstruction().getResponseWriter().setResultCode(ResponseWriter.ResultCode.WARNING).setResultMessage(UNABLE_TO_DESERIALZE_EXTRACT_MESSAGE + (e != null ? ": " + e.getMessage() : ""));
        }

        activityConfiguration.setExtract(extract);
        saveConfiguration();
    }

    private void fetchParameters() {
        ConditionRequestReader requestReader = getInstruction().getRequestReader();
        processPath = requestReader.getProcessPath();
        activityId = requestReader.getActivityId();
        extractAsString = requestReader.getExtract();
    }

    @Override
    protected void setInstructionResponse() {

    }

    @Override
    protected void logFinishedProcessing() {
        super.logFinishedProcessing();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Configure extract for {}", processPath);
        }
    }
}
