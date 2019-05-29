package com.im.njams.sdk.communication_rework.instruction.processor;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.communication_rework.instruction.InstructionSupport;
import com.im.njams.sdk.configuration.Configuration;
import com.im.njams.sdk.configuration.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Record extends ConfigurationInstructionProcessor{

    private static final Logger LOG = LoggerFactory.getLogger(Record.class);

    public static final String RECORD = Command.RECORD.commandString();

    public Record(Configuration configuration, String commandToProcess) {
        super(configuration, commandToProcess);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        //fetch parameters
        if (instructionSupport.hasParameter("EngineWideRecording")) {
            try {
                final boolean engineWideRecording = instructionSupport.getBoolParameter("EngineWideRecording");
                configuration.setRecording(engineWideRecording);
                //reset to default after logic change
                configuration.getProcesses().values().forEach(p -> p.setRecording(engineWideRecording));
            } catch (final Exception e) {
                instructionSupport.error("Unable to set client recording", e);
                return;
            }
        }

        final String processPath = instructionSupport.getProcessPath();
        if (processPath != null) {
            try {
                ProcessConfiguration process = null;
                process = configuration.getProcess(processPath);
                if (process == null) {
                    process = new ProcessConfiguration();
                    configuration.getProcesses().put(processPath, process);
                }
                final String doRecordParameter = instructionSupport.getParameter("Record");
                final boolean doRecord = "all".equalsIgnoreCase(doRecordParameter);
                process.setRecording(doRecord);
            } catch (final Exception e) {
                instructionSupport.error("Unable to set process recording", e);
                return;
            }
        }

        saveConfiguration(instructionSupport);
        LOG.debug("Recording for {}", processPath);
    }
}
