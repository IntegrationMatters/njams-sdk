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
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class GetExtractProcessor extends AbstractConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(
            GetExtractProcessor.class);

    public GetExtractProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH, InstructionSupport.ACTIVITY_ID)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        final ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            instructionSupport.error("Process " + processPath + " not found");
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " for process " + processPath + " not found");
            return;
        }
        final Extract extract = activity.getExtract();
        if (extract == null) {
            instructionSupport.error("Extract for activity " + activityId + " for process " + processPath + " not found");
            return;
        }
        try {
            final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
            instructionSupport.setParameter("extract", mapper.writeValueAsString(extract));
        } catch (final Exception e) {
            instructionSupport.error("Unable to serialize Extract", e);
            return;
        }

        LOG.debug("Get Extract for {} -> {}", processPath, activityId);
    }
}
