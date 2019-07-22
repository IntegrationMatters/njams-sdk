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
import com.faizsiegeln.njams.messageformat.v4.projectmessage.LogMode;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.configuration.entity.ProcessConfiguration;

/**
 * Todo: Write Doc
 */
public class SetLogLevelProcessor extends ConfigurationProcessor {

    /**
     * Todo: Write Doc
     */
    public static final String SET_LOG_LEVEL = Command.SET_LOG_LEVEL.commandString();

    public SetLogLevelProcessor(Njams njams) {
        super(njams, SET_LOG_LEVEL);
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
        ProcessConfiguration process = njams.getProcessFromConfiguration(processPath);
        if (process == null) {
            process = new ProcessConfiguration();
            njams.getProcessesFromConfiguration().put(processPath, process);
        }
        final LogMode logMode = instructionSupport.getEnumParameter(InstructionSupport.LOG_MODE, LogMode.class);
        if (logMode != null) {
            njams.setLogModeToConfiguration(logMode);
        }
        process.setLogLevel(loglevel);
        process.setExclude(instructionSupport.getBoolParameter("exclude"));
        saveConfiguration(instructionSupport);
    }
}