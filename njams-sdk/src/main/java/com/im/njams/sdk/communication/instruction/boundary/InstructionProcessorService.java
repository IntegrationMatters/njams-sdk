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
package com.im.njams.sdk.communication.instruction.boundary;

import com.im.njams.sdk.adapter.messageformat.command.entity.AbstractInstruction;
import com.im.njams.sdk.api.adapter.messageformat.command.entity.Instruction;
import com.im.njams.sdk.api.communication.instruction.control.InstructionProcessor;
import com.im.njams.sdk.communication.instruction.boundary.logging.InstructionLoggerFactory;
import com.im.njams.sdk.communication.instruction.control.InstructionDispatcher;
import com.im.njams.sdk.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo: Write Doc
 */
public class InstructionProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorService.class);

    private InstructionLoggerFactory instructionLoggerFactory = new InstructionLoggerFactory();

    private InstructionDispatcher instructionDispatcher = new InstructionDispatcher();

    public synchronized void putInstructionProcessor(String commandToListenTo,
            InstructionProcessor instructionProcessor) {
        if (instructionProcessor != null) {
            if (LOG.isDebugEnabled()) {
                if (StringUtils.isBlank(commandToListenTo)) {
                    LOG.warn("Cannot set InstructionProcessor for no command");
                } else {
                    InstructionProcessor oldInstructionProcessor = instructionDispatcher
                            .getInstructionProcessor(commandToListenTo);
                    if (oldInstructionProcessor != null) {
                        LOG.debug("Replacing InstructionProcessor {} for command {} by {}.",
                                oldInstructionProcessor.getClass().getSimpleName(), commandToListenTo,
                                instructionProcessor.getClass().getSimpleName());
                    }
                    this.instructionDispatcher.putInstructionProcessor(commandToListenTo, instructionProcessor);
                }
            }
        }
    }

    public InstructionProcessor getInstructionProcessor(String commandToListenTo) {
        return instructionDispatcher.getInstructionProcessor(commandToListenTo);
    }

    public synchronized void removeInstructionProcessor(String instructionProcessorCommand) {
        InstructionProcessor removedInstructionProcessor = instructionDispatcher
                .removeInstructionProcessor(instructionProcessorCommand);
        if (LOG.isDebugEnabled()) {
            if (removedInstructionProcessor != null) {
                LOG.debug("Removed InstructionProcessor {} for command {}.",
                        removedInstructionProcessor.getClass().getSimpleName(), instructionProcessorCommand);
            } else {
                LOG.debug("Can't remove InstructionProcessor for command {}, because it hasn't been added before.",
                        instructionProcessorCommand);
            }
        }
    }

    public synchronized void processInstruction(Instruction instruction) {
        if (instruction != null) {
            //log each instruction's request if available
            instructionLoggerFactory.getRequestLogger().log(((AbstractInstruction)instruction).getRealInstruction());

            //dispatch instruction to correct InstructionProcessor
            instructionDispatcher.dispatchInstruction(instruction);

            //log each instruction's response
            instructionLoggerFactory.getResponseLogger().log(((AbstractInstruction)instruction).getRealInstruction());
        } else if (LOG.isErrorEnabled()) {
            LOG.error("Instruction must not be null");
        }
    }

    public synchronized void stop() {
        instructionDispatcher.removeAllInstructionProcessors();
        instructionLoggerFactory.stop();
    }
}