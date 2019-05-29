package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.communication_rework.instruction.InstructionSupport;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import com.im.njams.sdk.configuration.TracepointExt;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class SetTracing extends ConfigurationInstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetTracing.class);

    public static final String SET_TRACING = Command.SET_TRACING.commandString();

    public SetTracing(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
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
            instructionSupport.error("Unable to parse end-time from tracepoint.", e);
            return;
        }
        if (endTime == null) {
            endTime = DateTimeUtility.now().plusMinutes(15);
        }
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

    private void updateTracePoint(final InstructionSupport instructionSupport, final LocalDateTime endTime) {
        LocalDateTime startTime;
        try {
            startTime = parseDateTime(instructionSupport.getParameter("starttime"));
        } catch (final Exception e) {
            instructionSupport.error("Unable to parse start-time from tracepoint.", e);
            return;
        }
        if (startTime == null) {
            startTime = DateTimeUtility.now();
        }

        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        //execute action
        ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            configuration.getProcesses().put(processPath, process);
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

    private void deleteTracePoint(final InstructionSupport instructionSupport) {
        //execute action
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();

        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            LOG.debug("Delete tracepoint: no process configuration for: {}", processPath);
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            LOG.debug("Delete tracepoint: no activity configuration for: {}#{}", processPath, activityId);
            return;
        }
        activity.setTracepoint(null);
        saveConfiguration(instructionSupport);
        LOG.debug("Tracepoint on {}#{} deleted", processPath, activityId);
    }
}
