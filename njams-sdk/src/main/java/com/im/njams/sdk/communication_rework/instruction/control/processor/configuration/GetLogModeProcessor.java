package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogModeProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogModeProcessor.class);

    public static final String GET_LOG_MODE = Command.GET_LOG_MODE.commandString();

    public GetLogModeProcessor(Njams njams) {
        super(njams, GET_LOG_MODE);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        final LogMode savedLogMode = njams.getLogModeFromConfiguration();
        instructionSupport.setParameter(InstructionSupport.LOG_MODE, savedLogMode);
        LOG.debug("Return LogMode: {}", savedLogMode);
    }
}
