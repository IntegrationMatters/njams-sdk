package com.im.njams.sdk.communication_rework.instruction.controller;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.dispatcher.InstructionDispatcher;
import com.im.njams.sdk.communication_rework.instruction.controller.logging.InstructionLoggerFactory;
import com.im.njams.sdk.communication_rework.instruction.processor.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstructionProcessorController {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorController.class);

    private InstructionDispatcher instructionDispatcher;

    private InstructionLoggerFactory instructionLoggerFactory;

    public InstructionProcessorController() {
        this.instructionDispatcher = new InstructionDispatcher();
        this.instructionLoggerFactory = new InstructionLoggerFactory();
    }

    public void addInstructionProcessor(InstructionProcessor instructionProcessor) {
        this.instructionDispatcher.addInstructionProcessor(instructionProcessor);
    }

    public void removeInstructionProcessor(String instructionProcessorCommandName) {
        InstructionProcessor instructionProcessorToRemove = instructionDispatcher.getInstructionProcessor(instructionProcessorCommandName);
        if (instructionProcessorToRemove != null) {
            instructionDispatcher.removeInstructionProcessor(instructionProcessorToRemove);
        }
    }

    public synchronized void processInstruction(Instruction instruction) {
        if (instruction != null) {
            //log each instruction's request if available
            instructionLoggerFactory.getRequestLogger().log(instruction);

            //dispatch instruction to correct InstructionProcessor
            instructionDispatcher.dispatchInstruction(instruction);

            //log each instruction's response
            instructionLoggerFactory.getResponseLogger().log(instruction);
        } else if (LOG.isErrorEnabled()) {
            LOG.error("Instruction must not be null");
        }
    }
}