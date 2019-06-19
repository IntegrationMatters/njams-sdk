package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

public class DeleteExtractProcessor extends ConfigurationProcessor {

    public static final String DELETE_EXTRACT = Command.DELETE_EXTRACT.commandString();

    public DeleteExtractProcessor(Njams njams) {
        super(njams, DELETE_EXTRACT);
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
        ProcessConfiguration process = null;
        process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            instructionSupport.error("Process configuration " + processPath + " not found");
            return;
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " for process " + processPath + " not found");
            return;
        }
        activity.setExtract(null);
        saveConfiguration(instructionSupport);
    }
}
