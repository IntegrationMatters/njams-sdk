package com.im.njams.sdk.communication_rework.instruction.boundary;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;
import com.im.njams.sdk.communication_rework.instruction.boundary.logging.InstructionLoggerFactory;
import com.im.njams.sdk.communication_rework.instruction.control.InstructionDispatcher;
import com.im.njams.sdk.communication_rework.instruction.control.processor.InstructionProcessor;
import com.im.njams.sdk.communication_rework.instruction.control.processor.replay.ReplayHandler;
import com.im.njams.sdk.communication_rework.instruction.control.processor.replay.ReplayProcessor;
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

    public void addInstructionProcessor(InstructionProcessor newInstructionProcessor) {
        if (newInstructionProcessor != null) {
            if (LOG.isDebugEnabled()) {
                String commandToProcessByNewProcessor = newInstructionProcessor.getCommandToProcess();
                if (commandToProcessByNewProcessor == null) {
                    LOG.warn("Cannot set InstructionProcessor for no command");
                } else {
                    InstructionProcessor oldInstructionProcessor = getInstructionProcessorFromDispatcher(commandToProcessByNewProcessor);
                    if (oldInstructionProcessor != null) {
                        LOG.debug("Replacing InstructionProcessor {} for command {} by {}.",
                                oldInstructionProcessor.getClass().getSimpleName(), commandToProcessByNewProcessor, newInstructionProcessor.getClass().getSimpleName());
                    }
                }
            }
        }
        this.instructionDispatcher.addInstructionProcessorForDistinctCommand(newInstructionProcessor);
    }

    private InstructionProcessor getInstructionProcessorFromDispatcher(String commandToLookFor) {
        return instructionDispatcher.getInstructionProcessorForDistinctCommand(commandToLookFor);
    }

    public void removeInstructionProcessor(String instructionProcessorCommand) {
        InstructionProcessor instructionProcessorToRemove = getInstructionProcessorFromDispatcher(instructionProcessorCommand);
        boolean isDebugEnabled = LOG.isDebugEnabled();
        if (instructionProcessorToRemove != null) {
            if (isDebugEnabled) {
                LOG.debug("Removing InstructionProcessor {} for command {}.", instructionProcessorToRemove.getClass().getSimpleName(), instructionProcessorCommand);
            }
            instructionDispatcher.removeInstructionProcessorForDistinctCommand(instructionProcessorToRemove);
        } else {
            if (isDebugEnabled) {
                LOG.debug("Can't remove InstructionListener for command {}, because it hasn't been added before.", instructionProcessorCommand);
            }
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

    public void setReplayHandlerToReplayProcessor(ReplayHandler replayHandler) {
        if (instructionDispatcher.containsInstructionProcessorForDistinctCommand(ReplayProcessor.REPLAY)) {
            ReplayProcessor replayProcessor = (ReplayProcessor) instructionDispatcher.getInstructionProcessorForDistinctCommand(ReplayProcessor.REPLAY);
            replayProcessor.setReplayHandler(replayHandler);
            if (replayHandler == null) {
                LOG.info("ReplayHandler has been removed successfully.");
            } else {
                LOG.info("ReplayHandler has been set successfully.");
            }
        } else {
            LOG.error("There is no Replay Processor available in the InstructionDispatcher");
        }
    }

    public ReplayHandler getReplayHandlerFromReplayProcessor() {
        ReplayProcessor replayProcessor = (ReplayProcessor) instructionDispatcher.getInstructionProcessorForDistinctCommand(ReplayProcessor.REPLAY);
        return replayProcessor.getReplayHandler();
    }

    public void stop() {
        instructionDispatcher.removeAllInstructionProcessors();
        instructionLoggerFactory.stop();
    }
}