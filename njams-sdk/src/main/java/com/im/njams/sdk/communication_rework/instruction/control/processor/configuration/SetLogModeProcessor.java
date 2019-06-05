package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.configuration.entity.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class SetLogModeProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetLogModeProcessor.class);

    public static final String SET_LOG_MODE = Command.SET_LOG_MODE.commandString();

    public SetLogModeProcessor(Properties properties, String commandToProcess) {
        super(properties, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.LOG_MODE) || !instructionSupport.validate(InstructionSupport.LOG_MODE, LogMode.class)) {
            return;
        }
        Configuration configuration = getConfiguration();
        //fetch parameters
        final LogMode logMode = instructionSupport.getEnumParameter(InstructionSupport.LOG_MODE, LogMode.class);
        configuration.setLogMode(logMode);
        saveConfiguration(configuration, instructionSupport);
        LOG.debug("Set LogMode to {}", logMode);
    }
}
