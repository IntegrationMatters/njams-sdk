package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.Extract;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.configuration.entity.ActivityConfiguration;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigureExtractProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigureExtractProcessor.class);

    public static final String CONFIGURE_EXTRACT = Command.CONFIGURE_EXTRACT.commandString();

    public ConfigureExtractProcessor(Njams njams, String commandToProcess) {
        super(njams, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH, InstructionSupport.ACTIVITY_ID, "extract")) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final String activityId = instructionSupport.getActivityId();
        final String extractString = instructionSupport.getParameter("extract");

        //execute action
        ProcessConfiguration process = configurationProxy.getProcess(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            configurationProxy.getProcesses().put(processPath, process);
        }
        ActivityConfiguration activity = null;
        activity = process.getActivity(activityId);
        if (activity == null) {
            activity = new ActivityConfiguration();
            process.getActivities().put(activityId, activity);
        }
        Extract extract = null;
        try {
            final ObjectMapper mapper = JsonSerializerFactory.getDefaultMapper();
            extract = mapper.readValue(extractString, Extract.class);
        } catch (final Exception e) {
            instructionSupport.error("Unable to deserialize extract", e);
            return;
        }
        activity.setExtract(extract);
        saveConfiguration(instructionSupport);
        LOG.debug("Configure extract for {}", processPath);
    }
}
