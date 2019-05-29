package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.communication_rework.instruction.InstructionSupport;
import com.im.njams.sdk.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogMode extends ConfigurationInstructionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogMode.class);

    public static final String GET_LOG_MODE = Command.GET_LOG_MODE.commandString();

    public GetLogMode(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        instructionSupport.setParameter(InstructionSupport.LOG_MODE, configuration.getLogMode());
        LOG.debug("Return LogMode: {}", configuration.getLogMode());
    }
}
