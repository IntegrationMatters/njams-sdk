package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.im.njams.sdk.communication_rework.instruction.InstructionSupport;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogLevel extends ConfigurationInstructionProcessor{

    private static final Logger LOG = LoggerFactory.getLogger(GetLogLevel.class);

    public static final String GET_LOG_LEVEL = Command.GET_LOG_LEVEL.commandString();

    public GetLogLevel(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
    }

    @Override
    public void processInstruction(InstructionSupport instructionSupport) {
        //fetch parameters
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH)) {
            return;
        }
        final String processPath = instructionSupport.getProcessPath();

        //execute action
        // init with defaults
        LogLevel logLevel = LogLevel.INFO;
        boolean exclude = false;

        // differing config stored?
        final ProcessConfiguration process = configuration.getProcess(processPath);
        if (process != null) {
            logLevel = process.getLogLevel();
            exclude = process.isExclude();
        }

        instructionSupport.setParameter(InstructionSupport.LOG_LEVEL, logLevel.name()).setParameter("exclude", exclude)
                .setParameter(InstructionSupport.LOG_MODE, configuration.getLogMode());

        LOG.debug("Return LogLevel for {}", processPath);
    }
}
