package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.communication_rework.instruction.entity.ActivityConfiguration;
import com.im.njams.sdk.communication_rework.instruction.entity.Configuration;
import com.im.njams.sdk.communication_rework.instruction.entity.ProcessConfiguration;

public class DeleteExtractProcessor extends ConfigurationProcessor {

    public static final String DELETE_EXTRACT = Command.DELETE_EXTRACT.commandString();

    public DeleteExtractProcessor(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
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
        process = configuration.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process configuration " + processPath + " not found");
            return;
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        activity.setExtract(null);
        saveConfiguration(instructionSupport);
    }
}
