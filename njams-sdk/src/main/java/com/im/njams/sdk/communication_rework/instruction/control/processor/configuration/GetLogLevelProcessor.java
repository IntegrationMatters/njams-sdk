/*
 * Copyright (c) 2019 Faiz & Siegeln Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.communication_rework.instruction.control.processor.configuration;

import com.faizsiegeln.njams.messageformat.v4.command.Command;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogLevel;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class GetLogLevelProcessor extends ConfigurationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GetLogLevelProcessor.class);

    /**
     * Todo: Write Doc
     */
    public static final String GET_LOG_LEVEL = Command.GET_LOG_LEVEL.commandString();

    public GetLogLevelProcessor(Njams njams) {
        super(njams, GET_LOG_LEVEL);
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
        final ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process != null) {
            logLevel = process.getLogLevel();
            exclude = process.isExclude();
        }

        instructionSupport.setParameter(InstructionSupport.LOG_LEVEL, logLevel.name()).setParameter("exclude", exclude)
                .setParameter(InstructionSupport.LOG_MODE, njams.getLogModeFromConfiguration());

        LOG.debug("Return LogLevel for {}", processPath);
    }
}
