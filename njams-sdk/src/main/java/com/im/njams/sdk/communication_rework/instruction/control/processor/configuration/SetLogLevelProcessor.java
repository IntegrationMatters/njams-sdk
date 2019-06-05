package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

public class SetLogLevelProcessor extends ConfigurationProcessor {

    public static final String SET_LOG_LEVEL = Command.SET_LOG_LEVEL.commandString();

    public SetLogLevelProcessor(Njams njams, String commandToProcess) {
        super(njams, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        if (!instructionSupport.validate(InstructionSupport.PROCESS_PATH, InstructionSupport.LOG_LEVEL)
                || !instructionSupport.validate(InstructionSupport.LOG_LEVEL, LogLevel.class)) {
            return;
        }
        //fetch parameters
        final String processPath = instructionSupport.getProcessPath();
        final LogLevel loglevel = instructionSupport.getEnumParameter(InstructionSupport.LOG_LEVEL, LogLevel.class);

        //execute action
        ProcessConfiguration process = configurationProxy.getProcess(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            configurationProxy.getProcesses().put(processPath, process);
        }
        final LogMode logMode = instructionSupport.getEnumParameter(InstructionSupport.LOG_MODE, LogMode.class);
        if (logMode != null) {
            configurationProxy.setLogMode(logMode);
        }
        process.setLogLevel(loglevel);
        process.setExclude(instructionSupport.getBoolParameter("exclude"));
        saveConfiguration(instructionSupport);
    }
}
