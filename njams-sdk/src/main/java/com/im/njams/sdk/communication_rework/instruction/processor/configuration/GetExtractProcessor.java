package com.im.njams.sdk.communication_rework.instruction.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.configuration.ActivityConfiguration;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetExtractProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetExtractProcessor.class);

    public static final String GET_EXTRACT = Command.GET_EXTRACT.commandString();

    public GetExtractProcessor(Configuration configuration, String commandToProcess) {
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
        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process == null) {
            instructionSupport.error("Process " + processPath + " not found");
            return;
        }
        final ActivityConfiguration activity = process.getActivity(activityId);
        if (activity == null) {
            instructionSupport.error("Activity " + activityId + " not found");
            return;
        }
        final Extract extract = activity.getExtract();
        if (extract == null) {
            instructionSupport.error("Extract for actvitiy " + activityId + " not found");
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
