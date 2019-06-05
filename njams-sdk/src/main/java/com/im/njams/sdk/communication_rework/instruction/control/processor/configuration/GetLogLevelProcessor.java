package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class GetLogLevelProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogLevelProcessor.class);

    public static final String GET_LOG_LEVEL = Command.GET_LOG_LEVEL.commandString();

    public GetLogLevelProcessor(Properties properties, String commandToProcess) {
        super(properties, commandToProcess);
    }

    @Override
    public void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();

        //execute action
        // init with defaults
        LogLevel logLevel = LogLevel.INFO;
        boolean exclude = false;

        // differing config stored?
        final ProcessConfiguration process = configurationProxy.getProcess(processPath);
        if (process != null) {
            logLevel = process.getLogLevel();
            exclude = process.isExclude();
        }

        instructionSupport.setParameter(InstructionSupport.LOG_LEVEL, logLevel.name()).setParameter("exclude", exclude)
                .setParameter(InstructionSupport.LOG_MODE, configurationProxy.getLogMode());

        LOG.debug("Return LogLevel for {}", processPath);
    }
}
