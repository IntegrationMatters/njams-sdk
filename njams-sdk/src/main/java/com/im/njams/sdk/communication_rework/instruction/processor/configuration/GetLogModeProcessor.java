package com.im.njams.sdk.communication_rework.instruction.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogModeProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogModeProcessor.class);

    public static final String GET_LOG_MODE = Command.GET_LOG_MODE.commandString();

    public GetLogModeProcessor(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        instructionSupport.setParameter(InstructionSupport.LOG_MODE, configuration.getLogMode());
        LOG.debug("Return LogMode: {}", configuration.getLogMode());
    }
}
