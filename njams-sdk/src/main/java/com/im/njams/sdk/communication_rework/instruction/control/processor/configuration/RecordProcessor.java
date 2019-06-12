package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RecordProcessor.class);

    public static final String RECORD = Command.RECORD.commandString();

    public RecordProcessor(Njams njams) {
        super(njams, RECORD);
    }

    @Override
    protected void processInstruction(InstructionSupport instructionSupport) {
        //fetch parameters
        if (instructionSupport.hasParameter("EngineWideRecording")) {
            try {
                final boolean engineWideRecording = instructionSupport.getBoolParameter("EngineWideRecording");
                njams.setRecordingToConfiguration(engineWideRecording);
                //reset to default after logic change
                njams.getProcessesFromConfiguration().values().forEach(p -> p.setRecording(engineWideRecording));
            } catch (final Exception e) {
                instructionSupport.error("Unable to set client recording", e);
                return;
            }
        }

        final String processPath = instructionSupport.getProcessPath();
        if (processPath != null) {
            try {
                ProcessConfiguration process = null;
                process = njams.getProcessFromConfiguration(processPath);
                if (process == null) {
                    process = new ProcessConfiguration();
                    njams.getProcessesFromConfiguration().put(processPath, process);
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
