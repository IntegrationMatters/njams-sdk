/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication.instruction.control.processor.configuration;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class RecordProcessor extends AbstractConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(
            RecordProcessor.class);

    public RecordProcessor(Njams njams) {
        super(njams);
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
                LOG.debug("EngineWideRecording has been set to {}", engineWideRecording);
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
                LOG.debug("Recording for {} set to {}", processPath, doRecord);
            } catch (final Exception e) {
                instructionSupport.error("Unable to set process recording", e);
                return;
            }
        }

        saveConfiguration(instructionSupport);
    }
}
