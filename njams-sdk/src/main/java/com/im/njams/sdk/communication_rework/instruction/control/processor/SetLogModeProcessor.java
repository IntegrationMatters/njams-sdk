package com.im.njams.sdk.communication_rework.instruction.control.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.communication_rework.instruction.control.InstructionSupport;
import com.im.njams.sdk.communication_rework.instruction.entity.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetLogModeProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetLogModeProcessor.class);

    public static final String SET_LOG_MODE = Command.SET_LOG_MODE.commandString();

    public SetLogModeProcessor(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.LOG_MODE) || !instructionSupport.validate(InstructionSupport.LOG_MODE, LogMode.class)) {
            return;
        }
        //fetch parameters
        final LogMode logMode = instructionSupport.getEnumParameter(InstructionSupport.LOG_MODE, LogMode.class);
        configuration.setLogMode(logMode);
        saveConfiguration(instructionSupport);
        LOG.debug("Set LogMode to {}", logMode);
    }
}
