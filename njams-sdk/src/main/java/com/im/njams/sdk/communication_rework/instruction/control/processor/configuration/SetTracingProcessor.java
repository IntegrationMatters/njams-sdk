package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.DateTimeUtility;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class SetTracingProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetTracingProcessor.class);

    public static final String SET_TRACING = Command.SET_TRACING.commandString();

    private static final int TRACING_IS_ENABLED_IN_MINUTES = 15;

    public SetTracingProcessor(Njams njams) {
        super(njams, SET_TRACING);
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
