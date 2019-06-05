package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import com.im.njams.sdk.configuration.entity.TracepointExt;

public class GetTracingProcessor extends ConfigurationProcessor {

    public static final String GET_TRACING = Command.GET_TRACING.commandString();

    public GetTracingProcessor(Njams njams, String commandToProcess) {
        super(njams, commandToProcess);
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
        final ProcessConfiguration process = configurationProxy.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process " + processPath + " not found");
            return;
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        TracepointExt tracepoint = null;
        tracepoint = activity.getTracepoint();
        if (tracepoint == null) {
            instructionSupport.error("Tracepoint for actvitiy " + activityId + " not found");
            return;
        }

        instructionSupport.setParameter("starttime", tracepoint.getStarttime())
                .setParameter("endtime", tracepoint.getEndtime())
                .setParameter("iterations", tracepoint.getIterations())
                .setParameter("deepTrace", tracepoint.isDeeptrace());

    }
}
