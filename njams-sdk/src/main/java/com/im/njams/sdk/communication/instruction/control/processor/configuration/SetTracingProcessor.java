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
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Todo: Write Doc
 */
public class SetTracingProcessor extends AbstractConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(
            SetTracingProcessor.class);

    private static final int TRACING_IS_ENABLED_IN_MINUTES = 15;

    public SetTracingProcessor(Njams njams) {
        super(njams);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH, InstructionSupport.ACTIVITY_ID)) {
            return;
        }
        //fetch parameters

        LocalDateTime endTime;
        try {
            endTime = parseDateTime(instructionSupport.getParameter("endtime"));
        } catch (final Exception e) {
            instructionSupport.error("Unable to parse endtime for tracepoint from instruction.", e);
            return;
        }
        if (endTime == null) {
            endTime = DateTimeUtility.now().plusMinutes(TRACING_IS_ENABLED_IN_MINUTES);
        }
        //TODO: is enable Tracing maybe a validatable? Because without enableTracing set, the Tracepoint will be deleted.
        if (instructionSupport.getBoolParameter("enableTracing") && endTime.isAfter(DateTimeUtility.now())) {
            LOG.debug("Update tracepoint.");
            updateTracePoint(instructionSupport, endTime);
        } else {
            LOG.debug("Delete tracepoint.");
            deleteTracePoint(instructionSupport);
        }
    }

    private LocalDateTime parseDateTime(final String dateTime) {
        if (StringUtils.isBlank(dateTime)) {
            return null;
        }
        return DateTimeUtility.fromString(dateTime);
    }

    void updateTracePoint(final InstructionSupport instructionSupport, final LocalDateTime endTime) {
        LocalDateTime startTime;
        //Todo: It is possible to set the TracePoint end before the TracePoint start, which makes no sense
        try {
            startTime = parseDateTime(instructionSupport.getParameter("starttime"));
        } catch (final Exception e) {
            instructionSupport.error("Unable to parse starttime for tracepoint from instruction.", e);
            return;
        }
        if (startTime == null) {
            startTime = DateTimeUtility.now();
        }

        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            njams.getProcessesFromConfiguration().put(processPath, process);
        }
        ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            activity = new ActivityConfiguration();
            process.getActivities().put(activityId, activity);
        }
        final TracepointExt tp = new TracepointExt();
        tp.setStarttime(startTime);
        tp.setEndtime(endTime);
        tp.setIterations(instructionSupport.getIntParameter("iterations"));
        tp.setDeeptrace(instructionSupport.getBoolParameter("deepTrace"));
        activity.setTracepoint(tp);
        saveConfiguration(instructionSupport);
        LOG.debug("Tracepoint on {}#{} updated", processPath, activityId);
    }

    void deleteTracePoint(final InstructionSupport instructionSupport) {
        //execute action
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        final ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            LOG.debug("Can't delete tracepoint: no process configuration for: {}", processPath);
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            LOG.debug("Can't delete tracepoint: no activity configuration for: {}#{}", processPath, activityId);
            return;
        }
        activity.setTracepoint(null);
        saveConfiguration(instructionSupport);
        LOG.debug("Tracepoint on {}#{} deleted", processPath, activityId);
    }
}
