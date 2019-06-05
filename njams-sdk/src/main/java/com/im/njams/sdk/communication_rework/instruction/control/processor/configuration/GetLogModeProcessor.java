package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class GetLogModeProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogModeProcessor.class);

    public static final String GET_LOG_MODE = Command.GET_LOG_MODE.commandString();

    public GetLogModeProcessor(Properties properties, String commandToProcess) {
        super(properties, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        instructionSupport.setParameter(InstructionSupport.LOG_MODE, configurationProxy.getLogMode());
        LOG.debug("Return LogMode: {}", configurationProxy.getLogMode());
    }
}
