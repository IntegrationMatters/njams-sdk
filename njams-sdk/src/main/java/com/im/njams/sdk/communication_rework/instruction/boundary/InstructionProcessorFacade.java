package com.im.njams.sdk.communication_rework.instruction.boundary;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.boundary.logging.InstructionLoggerFactory;
import com.im.njams.sdk.communication_rework.instruction.control.InstructionDispatcher;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstructionProcessorFacade {

    private static final Logger LOG = LoggerFactory.getLogger(InstructionProcessorFacade.class);

    private InstructionDispatcher instructionDispatcher;

    private InstructionLoggerFactory instructionLoggerFactory;

    public InstructionProcessorFacade() {
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